package com.carddemo.copybook.parser;

import com.carddemo.copybook.layout.Condition;
import com.carddemo.copybook.layout.LayoutNode;
import com.carddemo.copybook.layout.Picture;
import com.carddemo.copybook.layout.RecordLayout;
import com.carddemo.copybook.layout.Usage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dedicated parser for the COBOL data-description subset used by CardDemo (ADR-004): fixed-format
 * source, levels 01-49/77/88, {@code PIC}, {@code USAGE}, {@code VALUE(S)}, {@code OCCURS [n TO] m
 * [DEPENDING ON x]}, {@code REDEFINES}. Embedded {@code EXEC SQL ... END-EXEC} blocks (DCLGEN) and
 * procedural copybooks are skipped. Produces one {@link RecordLayout} per 01-level; copybooks whose
 * first level is not 01 (e.g. {@code CIPAUDTY}, {@code CSUTLDWY}) get a synthetic 01 named after
 * the copybook.
 */
public final class CopybookParser {

  private static final Set<String> NOISE =
      Set.of("IS", "ARE", "TIMES", "ON", "BY", "KEY", "SYNC", "SYNCHRONIZED", "GLOBAL", "EXTERNAL");

  private CopybookParser() {}

  /** Parses a copybook. */
  public static List<RecordLayout> parse(String copybook, String source) {
    return build(copybook, entries(statements(codeArea(source))));
  }

  /**
   * Parses the data division of a program and returns the 01-level records with the given names (in
   * the requested order), e.g. the FD records of {@code CBACT01C}.
   */
  public static List<RecordLayout> parseProgram(String program, String source, List<String> names) {
    String code = codeArea(source);
    String upper = code.toUpperCase(Locale.ROOT);
    int start = upper.indexOf("DATA DIVISION");
    int end = upper.indexOf("PROCEDURE DIVISION");
    String data = code.substring(Math.max(start, 0), end < 0 ? code.length() : end);
    List<RecordLayout> all = build(program, entries(statements(data)));
    List<RecordLayout> out = new ArrayList<>();
    for (String n : names) {
      out.add(
          all.stream()
              .filter(r -> r.name().equals(n))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException(n + " not found in " + program)));
    }
    return out;
  }

  /** Columns 8-72 of every non-comment line of a fixed-format source, joined by newlines. */
  static String codeArea(String source) {
    StringBuilder sb = new StringBuilder();
    for (String line : source.split("\r?\n", -1)) {
      if (line.length() > 6 && (line.charAt(6) == '*' || line.charAt(6) == '/')) {
        continue;
      }
      String code = line.length() > 7 ? line.substring(7, Math.min(72, line.length())) : "";
      sb.append(code).append('\n');
    }
    return sb.toString();
  }

  /** Splits code into period-terminated statements, honouring quoted literals. */
  static List<List<String>> statements(String code) {
    List<List<String>> out = new ArrayList<>();
    List<String> tokens = new ArrayList<>();
    StringBuilder tok = new StringBuilder();
    boolean inSql = false;
    int i = 0;
    while (i < code.length()) {
      char c = code.charAt(i);
      if (c == '\'' || c == '"') {
        int close = code.indexOf(c, i + 1);
        if (close < 0) {
          close = code.length() - 1;
        }
        tok.append(code, i, close + 1);
        i = close + 1;
        continue;
      }
      boolean terminator =
          c == '.' && (i + 1 == code.length() || Character.isWhitespace(code.charAt(i + 1)));
      boolean separator =
          (c == ',' || c == ';')
              && (i + 1 == code.length() || Character.isWhitespace(code.charAt(i + 1)));
      if (Character.isWhitespace(c) || separator || terminator) {
        if (!tok.isEmpty()) {
          String t = tok.toString();
          String u = t.toUpperCase(Locale.ROOT);
          if (u.equals("EXEC")) {
            inSql = true;
          }
          if (!inSql) {
            tokens.add(t);
          }
          if (u.equals("END-EXEC")) {
            inSql = false;
          }
          tok.setLength(0);
        }
        if (terminator && !inSql && !tokens.isEmpty()) {
          out.add(tokens);
          tokens = new ArrayList<>();
        }
        i++;
        continue;
      }
      tok.append(c);
      i++;
    }
    if (!tok.isEmpty() && !inSql) {
      tokens.add(tok.toString());
    }
    if (!tokens.isEmpty()) {
      out.add(tokens);
    }
    return out;
  }

  /** Parsed data-description entry before layout resolution. */
  record Entry(
      int level,
      String name,
      String pic,
      Usage usage,
      int occurs,
      int minOccurs,
      String dependingOn,
      String redefines,
      List<Condition.Range> values) {}

  static List<Entry> entries(List<List<String>> statements) {
    List<Entry> out = new ArrayList<>();
    for (List<String> s : statements) {
      if (s.isEmpty() || !s.get(0).chars().allMatch(Character::isDigit)) {
        continue;
      }
      out.add(entry(s));
    }
    return out;
  }

  private static Entry entry(List<String> s) {
    int level = Integer.parseInt(s.get(0));
    int i = 1;
    String name = "FILLER";
    if (i < s.size() && !isClauseKeyword(upper(s.get(i)))) {
      name = upper(s.get(i));
      i++;
    }
    String pic = null;
    Usage usage = null;
    int occurs = 1;
    int minOccurs = 1;
    String dependingOn = null;
    String redefines = null;
    List<Condition.Range> values = new ArrayList<>();
    while (i < s.size()) {
      String t = upper(s.get(i));
      if (NOISE.contains(t)) {
        i++;
        continue;
      }
      switch (t) {
        case "PIC", "PICTURE" -> {
          i = skip(s, i + 1, "IS");
          pic = s.get(i).toUpperCase(Locale.ROOT);
          i++;
        }
        case "USAGE" -> {
          i = skip(s, i + 1, "IS");
          usage = Usage.fromKeyword(upper(s.get(i)));
          i++;
        }
        case "REDEFINES" -> {
          redefines = upper(s.get(i + 1));
          i += 2;
        }
        case "OCCURS" -> {
          int n = Integer.parseInt(s.get(i + 1));
          i += 2;
          occurs = n;
          minOccurs = n;
          if (i < s.size() && upper(s.get(i)).equals("TO")) {
            occurs = Integer.parseInt(s.get(i + 1));
            i += 2;
          }
          i = skip(s, i, "TIMES");
          if (i < s.size() && upper(s.get(i)).equals("DEPENDING")) {
            i = skip(s, i + 1, "ON");
            dependingOn = upper(s.get(i));
            i++;
          }
        }
        case "INDEXED", "ASCENDING", "DESCENDING" -> {
          i++;
          while (i < s.size() && !isClauseKeyword(upper(s.get(i)))) {
            i++;
          }
        }
        case "VALUE", "VALUES" -> {
          i = skip(s, i + 1, "IS", "ARE");
          while (i < s.size() && !isClauseKeyword(upper(s.get(i)))) {
            String from = literal(s.get(i));
            String to = from;
            if (i + 2 < s.size() && Set.of("THRU", "THROUGH").contains(upper(s.get(i + 1)))) {
              to = literal(s.get(i + 2));
              i += 2;
            }
            values.add(new Condition.Range(from, to));
            i++;
          }
        }
        case "JUST", "JUSTIFIED", "BLANK" -> {
          i++;
          while (i < s.size()
              && Set.of("RIGHT", "WHEN", "ZERO", "ZEROS").contains(upper(s.get(i)))) {
            i++;
          }
        }
        case "SIGN", "LEADING", "TRAILING", "SEPARATE", "CHARACTER" ->
            throw new IllegalArgumentException("SIGN clause not supported on " + name);
        default -> {
          Usage u = Usage.fromKeyword(t);
          if (u == null) {
            throw new IllegalArgumentException("Unexpected token '" + t + "' in " + s);
          }
          usage = u;
          i++;
        }
      }
    }
    return new Entry(level, name, pic, usage, occurs, minOccurs, dependingOn, redefines, values);
  }

  private static String literal(String token) {
    return token.startsWith("'") || token.startsWith("\"") ? token : upper(token);
  }

  private static int skip(List<String> s, int i, String... words) {
    while (i < s.size() && Set.of(words).contains(upper(s.get(i)))) {
      i++;
    }
    return i;
  }

  private static boolean isClauseKeyword(String t) {
    return Set.of(
                "PIC",
                "PICTURE",
                "USAGE",
                "REDEFINES",
                "OCCURS",
                "VALUE",
                "VALUES",
                "INDEXED",
                "ASCENDING",
                "DESCENDING",
                "JUST",
                "JUSTIFIED",
                "BLANK",
                "SIGN",
                "SYNC",
                "SYNCHRONIZED",
                "EXTERNAL",
                "GLOBAL")
            .contains(t)
        || Usage.fromKeywordOrNull(t) != null;
  }

  private static String upper(String s) {
    return s.toUpperCase(Locale.ROOT);
  }

  // ---------------------------------------------------------------- tree + layout resolution

  private static final class Draft {
    final Entry entry;
    final List<Draft> children = new ArrayList<>();
    final List<Condition> conditions = new ArrayList<>();

    Draft(Entry entry) {
      this.entry = entry;
    }
  }

  private static List<RecordLayout> build(String copybook, List<Entry> entries) {
    List<Draft> roots = new ArrayList<>();
    List<Draft> stack = new ArrayList<>();
    Draft lastItem = null;
    for (Entry e : entries) {
      if (e.level() == 88) {
        if (lastItem == null) {
          throw new IllegalArgumentException("88 " + e.name() + " without item in " + copybook);
        }
        lastItem.conditions.add(new Condition(e.name(), e.values()));
        continue;
      }
      if (e.level() == 66) {
        continue;
      }
      int level = e.level() == 77 ? 1 : e.level();
      Draft d = new Draft(e);
      while (!stack.isEmpty() && level(stack.get(stack.size() - 1)) >= level) {
        stack.remove(stack.size() - 1);
      }
      if (stack.isEmpty()) {
        if (level != 1) {
          Draft synthetic =
              new Draft(new Entry(1, copybook, null, null, 1, 1, null, null, List.of()));
          roots.add(synthetic);
          stack.add(synthetic);
        } else {
          roots.add(d);
        }
      }
      if (!stack.isEmpty()) {
        stack.get(stack.size() - 1).children.add(d);
      }
      stack.add(d);
      lastItem = d;
    }
    List<RecordLayout> out = new ArrayList<>();
    for (Draft r : roots) {
      out.add(new RecordLayout(copybook, resolve(r, 0, Usage.DISPLAY)));
    }
    return out;
  }

  private static int level(Draft d) {
    return d.entry.level() == 77 ? 1 : d.entry.level();
  }

  private static LayoutNode resolve(Draft d, int offset, Usage inherited) {
    Entry e = d.entry;
    Usage usage = e.usage() != null ? e.usage() : inherited;
    if (e.pic() == null && d.children.isEmpty()) {
      if (usage == Usage.DISPLAY) {
        throw new IllegalArgumentException(e.name() + " has neither PIC nor children");
      }
      throw new IllegalArgumentException(e.name() + ": usage without PIC is not supported");
    }
    if (e.pic() != null) {
      Picture p = Picture.parse(e.pic());
      Usage effective = p.numeric() ? usage : Usage.DISPLAY;
      return new LayoutNode(
          e.level(),
          e.name(),
          p,
          effective,
          offset,
          p.storageLength(effective),
          e.occurs(),
          e.minOccurs(),
          e.dependingOn(),
          e.redefines(),
          List.of(),
          d.conditions);
    }
    List<LayoutNode> kids = new ArrayList<>();
    int pos = offset;
    int end = offset;
    for (Draft c : d.children) {
      int start = pos;
      if (c.entry.redefines() != null) {
        LayoutNode target =
            kids.stream()
                .filter(k -> k.name().equals(c.entry.redefines()))
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            c.entry.name() + " REDEFINES unknown " + c.entry.redefines()));
        start = target.offset();
      }
      LayoutNode child = resolve(c, start, usage);
      kids.add(child);
      int childEnd = start + child.totalLength();
      // the next item starts after the longest view of the REDEFINES family
      pos = c.entry.redefines() == null ? childEnd : Math.max(pos, childEnd);
      end = Math.max(end, Math.max(pos, childEnd));
    }
    return new LayoutNode(
        e.level(),
        e.name(),
        null,
        usage,
        offset,
        end - offset,
        e.occurs(),
        e.minOccurs(),
        e.dependingOn(),
        e.redefines(),
        kids,
        d.conditions);
  }
}
