package com.carddemo.batch.orchestration.csd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DFHCSDUP SYSIN subset used by CBADMCDJ: {@code *} comments, commands DEFINE/DELETE/LIST/ADD/
 * INSTALL, keyword continuation lines, {@code &HLQ.} JCL symbol. Anything else is a syntax error.
 */
public final class CsdParser {

  private static final Set<String> COMMANDS = Set.of("DEFINE", "DELETE", "LIST", "ADD", "INSTALL");
  private static final Set<String> TYPES =
      Set.of("LIBRARY", "MAPSET", "PROGRAM", "TRANSACTION", "FILE", "TDQUEUE", "TRANCLASS");
  private static final Pattern KEYWORD = Pattern.compile("\\G\\s*([A-Z0-9]+)\\(([^()]*)\\)");
  private static final Pattern NAME = Pattern.compile("[A-Z0-9#@$]{1,8}");

  private CsdParser() {}

  /** DFHCSDUP commands that change the CSD, in SYSIN order. */
  public sealed interface CsdCommand permits Define, Delete {}

  public record Define(CsdDefinition definition) implements CsdCommand {}

  /**
   * {@code DELETE GROUP(g)} ({@code type}/{@code name} null) or {@code DELETE <type>(<name>)
   * GROUP(g)}.
   */
  public record Delete(String type, String name, String group) implements CsdCommand {}

  public static List<CsdDefinition> parse(List<String> lines, String hlq) {
    return commands(lines, hlq).stream()
        .filter(c -> c instanceof Define)
        .map(c -> ((Define) c).definition())
        .toList();
  }

  public static List<CsdCommand> commands(List<String> lines, String hlq) {
    List<String> statements = new ArrayList<>();
    StringBuilder current = null;
    int lineNo = 0;
    for (String raw : lines) {
      lineNo++;
      String line = raw.length() > 72 ? raw.substring(0, 72) : raw;
      if (line.isBlank() || line.startsWith("*")) {
        continue;
      }
      String text = line.strip().replace("&HLQ.", hlq).toUpperCase(Locale.ROOT);
      String verb = text.split("\\s+", 2)[0];
      if (COMMANDS.contains(verb)) {
        if (current != null) {
          statements.add(current.toString());
        }
        current = new StringBuilder(text);
      } else if (current != null && KEYWORD.matcher(text).lookingAt()) {
        current.append(' ').append(text);
      } else {
        throw new CsdSyntaxException(lineNo, "unrecognised statement: " + raw.strip());
      }
    }
    if (current != null) {
      statements.add(current.toString());
    }
    List<CsdCommand> commands = new ArrayList<>();
    for (String s : statements) {
      if (s.startsWith("DEFINE")) {
        commands.add(new Define(define(s.substring("DEFINE".length()))));
      } else if (s.startsWith("DELETE")) {
        commands.add(delete(s.substring("DELETE".length())));
      }
    }
    return commands;
  }

  private static Delete delete(String body) {
    Map<String, String> kw = keywords(body, "DELETE");
    String group = kw.remove("GROUP");
    if (group == null || !NAME.matcher(group).matches()) {
      throw new CsdSyntaxException(0, "DELETE without valid GROUP: " + body.strip());
    }
    if (kw.isEmpty()) {
      return new Delete(null, null, group);
    }
    Map.Entry<String, String> res = kw.entrySet().iterator().next();
    if (kw.size() > 1 || !TYPES.contains(res.getKey()) || !NAME.matcher(res.getValue()).matches()) {
      throw new CsdSyntaxException(0, "malformed DELETE: " + body.strip());
    }
    return new Delete(res.getKey(), res.getValue(), group);
  }

  private static Map<String, String> keywords(String body, String verb) {
    Map<String, String> kw = new LinkedHashMap<>();
    Matcher m = KEYWORD.matcher(body);
    int end = 0;
    while (m.find()) {
      kw.put(m.group(1), m.group(2).strip());
      end = m.end();
    }
    if (!body.substring(end).isBlank() || kw.isEmpty()) {
      throw new CsdSyntaxException(0, "malformed " + verb + ": " + body.strip());
    }
    return kw;
  }

  private static CsdDefinition define(String body) {
    Map<String, String> kw = keywords(body, "DEFINE");
    Map.Entry<String, String> first = kw.entrySet().iterator().next();
    String type = first.getKey();
    String name = first.getValue();
    String group = kw.get("GROUP");
    if (!TYPES.contains(type) || !NAME.matcher(name).matches()) {
      throw new CsdSyntaxException(0, "invalid resource " + type + "(" + name + ")");
    }
    if (group == null || !NAME.matcher(group).matches()) {
      throw new CsdSyntaxException(0, "DEFINE " + type + "(" + name + ") without valid GROUP");
    }
    Map<String, String> attrs = new LinkedHashMap<>(kw);
    attrs.remove(type);
    attrs.remove("GROUP");
    return new CsdDefinition(type, name, group, attrs);
  }

  /** DFHCSDUP syntax error (the utility ends with a severe-error return code). */
  public static class CsdSyntaxException extends RuntimeException {
    public CsdSyntaxException(int line, String message) {
      super((line > 0 ? "line " + line + ": " : "") + message);
    }
  }
}
