package com.carddemo.copybook.gen;

import com.carddemo.copybook.layout.Condition;
import com.carddemo.copybook.layout.LayoutNode;
import com.carddemo.copybook.layout.RecordLayout;
import com.carddemo.copybook.layout.Usage;
import com.carddemo.copybook.parser.CopybookParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Deterministic generator: copybooks of {@link GenerationSpec} → Java {@code record}s with a {@code
 * LAYOUT} constant in {@code com.carddemo.copybook.generated.*}. Same input, same bytes (no
 * timestamps, source order). Run {@code main(<repo root>, <src/main/java dir>)} to regenerate.
 */
public final class RecordGenerator {

  public static final String BASE_PACKAGE = "com.carddemo.copybook.generated";

  private static final Set<String> KEYWORDS =
      Set.of(
          "abstract",
          "assert",
          "boolean",
          "break",
          "byte",
          "case",
          "catch",
          "char",
          "class",
          "const",
          "continue",
          "default",
          "do",
          "double",
          "else",
          "enum",
          "extends",
          "final",
          "finally",
          "float",
          "for",
          "goto",
          "if",
          "implements",
          "import",
          "instanceof",
          "int",
          "interface",
          "long",
          "native",
          "new",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "short",
          "static",
          "strictfp",
          "super",
          "switch",
          "synchronized",
          "this",
          "throw",
          "throws",
          "transient",
          "try",
          "void",
          "volatile",
          "while",
          "record",
          "var",
          "yield",
          "sealed",
          "permits",
          "hashCode",
          "toString",
          "equals",
          "getClass");

  private final Path appDir;
  private final Map<String, String> files = new LinkedHashMap<>();
  private final List<String[]> index = new ArrayList<>();

  private RecordGenerator(Path appDir) {
    this.appDir = appDir;
  }

  /** Generates every source; returns relative path (from the source root) → content. */
  public static Map<String, String> generate(Path appDir) {
    RecordGenerator g = new RecordGenerator(appDir);
    for (GenerationSpec.Target t : GenerationSpec.TARGETS) {
      g.target(t);
    }
    for (GenerationSpec.Family f : GenerationSpec.FAMILIES) {
      g.family(f);
    }
    g.indexClass();
    return g.files;
  }

  public static void main(String[] args) throws IOException {
    Path app = Path.of(args.length > 0 ? args[0] : "../../app");
    Path out = Path.of(args.length > 1 ? args[1] : "src/main/java");
    Path pkgDir = out.resolve(BASE_PACKAGE.replace('.', '/'));
    if (Files.isDirectory(pkgDir)) {
      try (var s = Files.walk(pkgDir)) {
        for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
          Files.delete(p);
        }
      }
    }
    for (Map.Entry<String, String> e : generate(app).entrySet()) {
      Path p = out.resolve(e.getKey());
      Files.createDirectories(p.getParent());
      Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
    }
  }

  private String read(String source) {
    try {
      return Files.readString(appDir.resolve(source), StandardCharsets.ISO_8859_1);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<RecordLayout> layouts(GenerationSpec.Target t) {
    String text = read(t.source());
    return t.programRecords().isEmpty()
        ? CopybookParser.parse(t.id(), text)
        : CopybookParser.parseProgram(t.id(), text, t.programRecords());
  }

  private void target(GenerationSpec.Target t) {
    for (RecordLayout l : layouts(t)) {
      String name = t.names().getOrDefault(l.name(), upperCamel(l.name()));
      if (files.containsKey(path(t.pkg(), name))) {
        name = upperCamel(t.id()) + name;
      }
      Emitter e = new Emitter(t.source(), t.pkg(), name);
      e.topRecord(l, name, null, layoutLiteral(l, "    "));
      put(t.pkg(), name, e.finish());
      index.add(new String[] {l.copybook() + "/" + l.name(), t.pkg() + "." + name + ".LAYOUT"});
    }
  }

  private void family(GenerationSpec.Family f) {
    String source = GenerationSpec.FAMILY_SOURCES.get(f.id());
    String text = read(source);
    boolean byLength = f.discriminator() == null;
    List<String> names =
        byLength
            ? f.members().stream().map(GenerationSpec.Member::item).toList()
            : List.of(f.baseRecord());
    List<RecordLayout> parsed =
        source.endsWith(".cbl")
            ? CopybookParser.parseProgram(f.id(), text, names)
            : CopybookParser.parse(f.id(), text).stream()
                .filter(r -> r.name().equals(f.baseRecord()))
                .toList();
    List<String> permits = new ArrayList<>();
    f.members().forEach(m -> permits.add(m.javaName()));
    if (f.fallback() != null) {
      permits.add(f.fallback());
    }
    StringBuilder sb = new StringBuilder();
    Set<String> imports = new TreeSet<>();
    imports.add("com.carddemo.copybook.codec.CodecOptions");
    imports.add("com.carddemo.copybook.codec.VariantCodec");
    RecordLayout base = byLength ? null : parsed.get(0);
    StringBuilder body = new StringBuilder();
    if (base != null) {
      imports.add("com.carddemo.copybook.layout.RecordLayout");
      imports.add("com.carddemo.copybook.layout.LayoutNodes");
      imports.add("java.util.List");
      body.append("  /** Base layout: ")
          .append(base.name())
          .append(" (")
          .append(base.length())
          .append(" bytes) with every REDEFINES. */\n");
      body.append("  RecordLayout BASE =\n").append(layoutLiteral(base, "      ")).append(";\n\n");
      Emitter probe = new Emitter(source, f.pkg(), f.interfaceName());
      for (LayoutNode c : base.root().storageChildren()) {
        if (c.name().equals(f.baseItem())) {
          continue;
        }
        String type = probe.javaType(c, f.interfaceName());
        imports.addAll(probe.imports);
        body.append("  ").append(type).append(' ').append(probe.component(c)).append("();\n\n");
      }
    }
    int max =
        byLength ? parsed.stream().mapToInt(RecordLayout::length).max().orElse(0) : base.length();
    body.append("  /** Codec choosing the variant by ")
        .append(byLength ? "record length" : f.discriminator())
        .append(". */\n");
    body.append("  static VariantCodec<")
        .append(f.interfaceName())
        .append("> codec(CodecOptions options) {\n");
    body.append("    return VariantCodec.builder(")
        .append(f.interfaceName())
        .append(".class, ")
        .append(max)
        .append(", options)\n");
    body.append(
        byLength
            ? "        .byLength()\n"
            : "        .discriminator(BASE, \"" + f.discriminator() + "\")\n");
    for (GenerationSpec.Member m : f.members()) {
      body.append("        .variant(\"")
          .append(m.key())
          .append("\", ")
          .append(m.javaName())
          .append(".class, ")
          .append(m.javaName())
          .append(".LAYOUT)\n");
    }
    if (f.fallback() != null) {
      body.append("        .fallback(").append(f.fallback()).append(".class, BASE)\n");
    }
    body.append("        .build();\n  }\n");
    sb.append("package ").append(BASE_PACKAGE).append('.').append(f.pkg()).append(";\n\n");
    sb.append(header(source)).append('\n');
    imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
    sb.append("\n/** ")
        .append(f.id())
        .append(": sealed family discriminated by ")
        .append(byLength ? "VB record length" : f.discriminator())
        .append(". */\n");
    sb.append("public sealed interface ")
        .append(f.interfaceName())
        .append("\n    permits ")
        .append(String.join(", ", permits))
        .append(" {\n\n")
        .append(body)
        .append("}\n");
    put(f.pkg(), f.interfaceName(), sb.toString());
    if (base != null) {
      index.add(
          new String[] {
            base.copybook() + "/" + base.name(), f.pkg() + "." + f.interfaceName() + ".BASE"
          });
    }
    for (int i = 0; i < f.members().size(); i++) {
      GenerationSpec.Member m = f.members().get(i);
      Emitter e = new Emitter(source, f.pkg(), m.javaName());
      if (byLength) {
        RecordLayout l = parsed.get(i);
        e.topRecord(l, m.javaName(), f.interfaceName(), layoutLiteral(l, "    "));
        index.add(
            new String[] {l.copybook() + "/" + l.name(), f.pkg() + "." + m.javaName() + ".LAYOUT"});
      } else {
        RecordLayout v = base.variant(f.baseItem(), m.item());
        e.topRecord(
            v,
            m.javaName(),
            f.interfaceName(),
            "    "
                + f.interfaceName()
                + ".BASE.variant(\""
                + f.baseItem()
                + "\", \""
                + m.item()
                + "\")");
      }
      put(f.pkg(), m.javaName(), e.finish());
    }
    if (f.fallback() != null) {
      Emitter e = new Emitter(source, f.pkg(), f.fallback());
      e.topRecord(base, f.fallback(), f.interfaceName(), "    " + f.interfaceName() + ".BASE");
      put(f.pkg(), f.fallback(), e.finish());
    }
  }

  private void indexClass() {
    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(BASE_PACKAGE).append(";\n\n");
    sb.append(header("app/ (all GenerationSpec sources)")).append('\n');
    sb.append("import com.carddemo.copybook.layout.RecordLayout;\n");
    sb.append("import java.util.LinkedHashMap;\nimport java.util.Map;\n\n");
    sb.append("/** Index of every generated layout, keyed {@code <copybook>/<01 name>}. */\n");
    sb.append("public final class GeneratedLayouts {\n\n  private GeneratedLayouts() {}\n\n");
    sb.append("  /** All layouts in generation order. */\n");
    sb.append("  public static Map<String, RecordLayout> all() {\n");
    sb.append("    Map<String, RecordLayout> m = new LinkedHashMap<>();\n");
    for (String[] e : index) {
      sb.append("    m.put(\"")
          .append(e[0])
          .append("\", ")
          .append(BASE_PACKAGE)
          .append('.')
          .append(e[1])
          .append(");\n");
    }
    sb.append("    return m;\n  }\n}\n");
    files.put(BASE_PACKAGE.replace('.', '/') + "/GeneratedLayouts.java", sb.toString());
  }

  private static String path(String pkg, String name) {
    return (BASE_PACKAGE + "." + pkg).replace('.', '/') + "/" + name + ".java";
  }

  private void put(String pkg, String name, String content) {
    String key = path(pkg, name);
    if (files.put(key, content) != null) {
      throw new IllegalStateException("Duplicate generated type " + key);
    }
  }

  static String header(String source) {
    return "// GENERATED by com.carddemo.copybook.gen.RecordGenerator from app/"
        + source
        + " - do not edit by hand.\n";
  }

  // ------------------------------------------------------------------ layout literal

  static String layoutLiteral(RecordLayout l, String indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(indent)
        .append("new RecordLayout(\n")
        .append(indent)
        .append("    \"")
        .append(l.copybook())
        .append("\",\n");
    node(l.root(), indent + "    ", sb);
    sb.append(")");
    return sb.toString();
  }

  private static void node(LayoutNode n, String indent, StringBuilder sb) {
    sb.append(indent)
        .append("LayoutNodes.n(")
        .append(n.level())
        .append(", ")
        .append(str(n.name()))
        .append(", ")
        .append(n.picture() == null ? "null" : str(n.picture().source()))
        .append(", ")
        .append(str(n.usage().name()))
        .append(", ")
        .append(n.offset())
        .append(", ")
        .append(n.length())
        .append(", ")
        .append(n.occurs())
        .append(", ")
        .append(n.minOccurs())
        .append(", ")
        .append(n.dependingOn() == null ? "null" : str(n.dependingOn()))
        .append(", ")
        .append(n.redefines() == null ? "null" : str(n.redefines()))
        .append(", ");
    if (n.conditions().isEmpty()) {
      sb.append("List.of()");
    } else {
      sb.append("List.of(");
      for (int i = 0; i < n.conditions().size(); i++) {
        Condition c = n.conditions().get(i);
        sb.append(i == 0 ? "" : ", ").append("LayoutNodes.cond(").append(str(c.name()));
        for (Condition.Range r : c.values()) {
          sb.append(", ").append(str(r.from())).append(", ").append(str(r.to()));
        }
        sb.append(")");
      }
      sb.append(")");
    }
    for (LayoutNode c : n.children()) {
      sb.append(",\n");
      node(c, indent + "    ", sb);
    }
    sb.append(")");
  }

  static boolean hasConditions(LayoutNode n) {
    return !n.conditions().isEmpty()
        || n.children().stream().anyMatch(RecordGenerator::hasConditions);
  }

  static String str(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      if (c == '"' || c == '\\') {
        sb.append('\\').append(c);
      } else if (c < 0x20 || c > 0x7e) {
        sb.append(String.format("\\u%04x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.append('"').toString();
  }

  // ------------------------------------------------------------------ names

  static String upperCamel(String cobol) {
    StringBuilder sb = new StringBuilder();
    for (String part : cobol.toLowerCase(Locale.ROOT).split("[-_]")) {
      if (!part.isEmpty()) {
        sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
    }
    if (sb.isEmpty() || !Character.isJavaIdentifierStart(sb.charAt(0))) {
      sb.insert(0, 'N');
    }
    return sb.toString();
  }

  static String lowerCamel(String cobol) {
    String u = upperCamel(cobol);
    String l = Character.toLowerCase(u.charAt(0)) + u.substring(1);
    return KEYWORDS.contains(l) ? l + "Value" : l;
  }

  static String constant(String cobol) {
    String c = cobol.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    return Character.isDigit(c.charAt(0)) ? "N" + c : c;
  }

  // ------------------------------------------------------------------ record emission

  /** Emits one top-level record file (with nested records and enums). */
  private static final class Emitter {
    final String source;
    final String pkg;
    final Set<String> imports = new TreeSet<>();
    final Set<String> typeNames = new HashSet<>();
    final StringBuilder out = new StringBuilder();
    String topName;
    Map<LayoutNode, String> componentNames = new java.util.IdentityHashMap<>();
    Map<LayoutNode, String> groupTypes = new java.util.IdentityHashMap<>();

    Emitter(String source, String pkg, String topName) {
      this.source = source;
      this.pkg = pkg;
      this.topName = topName;
      typeNames.add(topName);
    }

    String component(LayoutNode n) {
      return componentNames.computeIfAbsent(n, k -> lowerCamel(k.name()));
    }

    void topRecord(RecordLayout l, String name, String iface, String layoutExpr) {
      imports.add("com.carddemo.copybook.codec.CodecOptions");
      imports.add("com.carddemo.copybook.codec.LayoutCodec");
      imports.add("com.carddemo.copybook.layout.RecordLayout");
      if (!layoutExpr.trim().startsWith(iface == null ? "\u0000" : iface + ".")) {
        imports.add("com.carddemo.copybook.layout.LayoutNodes");
        imports.add("java.util.List");
      }
      StringBuilder body = new StringBuilder();
      body.append("  /** Layout of ").append(l.name()).append(" (offsets, lengths, PIC). */\n");
      body.append("  public static final RecordLayout LAYOUT =\n")
          .append(layoutExpr)
          .append(";\n\n");
      body.append("  /** Codec over this layout. */\n");
      body.append("  public static LayoutCodec<")
          .append(name)
          .append("> codec(CodecOptions options) {\n    return LayoutCodec.of(LAYOUT, ")
          .append(name)
          .append(".class, options);\n  }\n");
      String javadoc =
          l.copybook()
              + " 01 "
              + l.name()
              + " ("
              + l.length()
              + " bytes"
              + (l.variableLength() ? " max, OCCURS DEPENDING ON" : "")
              + ").";
      recordDecl(l.root(), name, iface, javadoc, body.toString(), "", out);
    }

    private List<LayoutNode> items(LayoutNode group) {
      List<LayoutNode> items = group.storageChildren();
      return items.isEmpty() && group.picture() != null ? List.of(group) : items;
    }

    private void recordDecl(
        LayoutNode group,
        String name,
        String iface,
        String javadoc,
        String extraBody,
        String indent,
        StringBuilder sb) {
      List<LayoutNode> items = items(group);
      Set<String> used = new HashSet<>();
      int fillers = 0;
      for (LayoutNode c : items) {
        String cn = lowerCamel(c.name());
        if (c.filler()) {
          fillers++;
          cn = fillers == 1 ? "filler" : "filler" + fillers;
        }
        String base = cn;
        int k = 2;
        while (!used.add(cn)) {
          cn = base + k++;
        }
        componentNames.put(c, cn);
      }
      StringBuilder nested = new StringBuilder();
      List<String> comps = new ArrayList<>();
      for (LayoutNode c : items) {
        String type = javaType(c, name);
        comps.add(
            indent
                + "    // "
                + describe(c)
                + "\n"
                + indent
                + "    "
                + type
                + " "
                + componentNames.get(c));
      }
      sb.append(indent).append("/** ").append(javadoc).append(" */\n");
      sb.append(indent)
          .append("public record ")
          .append(name)
          .append("(\n")
          .append(String.join(",\n", comps))
          .append(")");
      if (iface != null) {
        sb.append(" implements ").append(iface);
      }
      sb.append(" {\n");
      boolean first = true;
      if (!extraBody.isEmpty()) {
        sb.append('\n').append(extraBody);
        first = false;
      }
      if (group.group() && !group.conditions().isEmpty() && alphanumericOnly(items)) {
        sb.append('\n').append(groupConditions(group, items, indent + "  "));
        first = false;
      }
      for (LayoutNode c : items) {
        if (!c.group() && !c.table() && !c.conditions().isEmpty()) {
          String member = conditions(c, indent + "  ");
          sb.append(first ? "\n" : "\n").append(member);
          first = false;
        }
      }
      for (LayoutNode c : items) {
        if (c.group()) {
          String typeName = groupTypes.get(c);
          String doc =
              c.name()
                  + " ("
                  + c.length()
                  + " bytes"
                  + (c.table() ? " x " + c.occurs() : "")
                  + ").";
          nested.append('\n');
          recordDecl(c, typeName, null, doc, "", indent + "  ", nested);
        }
      }
      sb.append(nested);
      if (first && nested.isEmpty()) {
        sb.append(indent).append("}\n");
      } else {
        sb.append(indent).append("}\n");
      }
    }

    String javaType(LayoutNode c, String owner) {
      String t;
      if (c.group()) {
        String typeName = groupTypes.get(c);
        if (typeName == null) {
          typeName = upperCamel(c.name());
          String base = typeName;
          int k = 2;
          while (!typeNames.add(typeName)) {
            typeName = base + k++;
          }
          groupTypes.put(c, typeName);
        }
        t = typeName;
      } else {
        t = scalar(c, c.table());
      }
      if (c.table()) {
        imports.add("java.util.List");
        return "List<" + t + ">";
      }
      return t;
    }

    private String scalar(LayoutNode c, boolean boxed) {
      if (!c.picture().numeric()) {
        return "String";
      }
      int digits = c.picture().digits();
      boolean wide = c.usage() == Usage.NATIVE_BINARY && !c.picture().signed();
      if (c.picture().scale() > 0
          || digits > 18
          || (c.usage() == Usage.NATIVE_BINARY && digits > 9)) {
        imports.add("java.math.BigDecimal");
        return "BigDecimal";
      }
      if (digits <= 9 && !(wide && digits > 4)) {
        return boxed ? "Integer" : "int";
      }
      return boxed ? "Long" : "long";
    }

    private static boolean alphanumericOnly(List<LayoutNode> items) {
      return items.stream()
          .allMatch(
              c ->
                  !c.group() && !c.table() && c.usage() == Usage.DISPLAY && !c.picture().numeric());
    }

    /** 88-levels on an alphanumeric group: tested against the concatenated group value. */
    private String groupConditions(LayoutNode group, List<LayoutNode> items, String indent) {
      String value = items.stream().map(componentNames::get).collect(Collectors.joining(" + "));
      StringBuilder sb = new StringBuilder();
      for (Condition cond : group.conditions()) {
        sb.append(indent).append("/** 88 ").append(cond.name()).append(" (group). */\n");
        sb.append(indent)
            .append("public boolean is")
            .append(upperCamel(cond.name()))
            .append("() {\n")
            .append(indent)
            .append("  return ")
            .append(topName)
            .append(".LAYOUT.condition(")
            .append(str(cond.name()))
            .append(").test(")
            .append(value)
            .append(", ")
            .append(group.length())
            .append(");\n")
            .append(indent)
            .append("}\n\n");
      }
      return sb.toString();
    }

    private String conditions(LayoutNode c, String indent) {
      StringBuilder sb = new StringBuilder();
      String comp = componentNames.get(c);
      for (Condition cond : c.conditions()) {
        sb.append(indent).append("/** 88 ").append(cond.name()).append(". */\n");
        sb.append(indent)
            .append("public boolean is")
            .append(upperCamel(cond.name()))
            .append("() {\n")
            .append(indent)
            .append("  return ")
            .append(topName)
            .append(".LAYOUT.condition(")
            .append(str(cond.name()))
            .append(").test(")
            .append(comp)
            .append(", ")
            .append(c.length())
            .append(");\n")
            .append(indent)
            .append("}\n\n");
      }
      boolean enumerable =
          !c.picture().numeric() && c.conditions().stream().allMatch(Condition::singleLiteral);
      if (enumerable) {
        imports.add("java.util.Optional");
        imports.add("com.carddemo.copybook.layout.Condition");
        String en = upperCamel(c.name()) + "Value";
        while (!typeNames.add(en)) {
          en = en + "s";
        }
        sb.append(indent).append("/** 88-level values of ").append(c.name()).append(". */\n");
        sb.append(indent).append("public enum ").append(en).append(" {\n");
        for (int i = 0; i < c.conditions().size(); i++) {
          Condition cond = c.conditions().get(i);
          sb.append(indent)
              .append("  ")
              .append(constant(cond.name()))
              .append('(')
              .append(str(cond.values().get(0).from()))
              .append(')')
              .append(i == c.conditions().size() - 1 ? ";\n\n" : ",\n");
        }
        sb.append(indent).append("  private final String literal;\n\n");
        sb.append(indent)
            .append("  ")
            .append(en)
            .append("(String literal) {\n")
            .append(indent)
            .append("    this.literal = literal;\n")
            .append(indent)
            .append("  }\n\n");
        sb.append(indent).append("  /** COBOL literal of the condition. */\n");
        sb.append(indent)
            .append("  public String literal() {\n")
            .append(indent)
            .append("    return literal;\n")
            .append(indent)
            .append("  }\n\n");
        sb.append(indent).append("  /** Condition matching a field value, if any. */\n");
        sb.append(indent)
            .append("  public static Optional<")
            .append(en)
            .append("> of(String value) {\n");
        sb.append(indent).append("    for (").append(en).append(" v : values()) {\n");
        sb.append(indent)
            .append("      if (Condition.alphanumeric(v.literal, ")
            .append(c.length())
            .append(").equals(value)) {\n");
        sb.append(indent)
            .append("        return Optional.of(v);\n")
            .append(indent)
            .append("      }\n")
            .append(indent)
            .append("    }\n");
        sb.append(indent)
            .append("    return Optional.empty();\n")
            .append(indent)
            .append("  }\n")
            .append(indent)
            .append("}\n\n");
        sb.append(indent).append("/** 88-level value of ").append(c.name()).append(". */\n");
        sb.append(indent)
            .append("public Optional<")
            .append(en)
            .append("> ")
            .append(comp)
            .append("Condition() {\n")
            .append(indent)
            .append("  return ")
            .append(en)
            .append(".of(")
            .append(comp)
            .append(");\n")
            .append(indent)
            .append("}\n\n");
      }
      return sb.toString();
    }

    private static String describe(LayoutNode c) {
      StringBuilder sb = new StringBuilder(String.format("%02d ", c.level())).append(c.name());
      if (c.picture() != null) {
        sb.append(" PIC ").append(c.picture().source());
        if (c.usage() != Usage.DISPLAY) {
          sb.append(' ').append(c.usage().name());
        }
      }
      if (c.table()) {
        sb.append(" OCCURS ")
            .append(c.minOccurs() != c.occurs() ? c.minOccurs() + " TO " : "")
            .append(c.occurs());
        if (c.dependingOn() != null) {
          sb.append(" DEPENDING ON ").append(c.dependingOn());
        }
      }
      return sb.append(" @").append(c.offset()).append(" len ").append(c.length()).toString();
    }

    String finish() {
      StringBuilder sb = new StringBuilder();
      sb.append("package ").append(BASE_PACKAGE).append('.').append(pkg).append(";\n\n");
      sb.append(header(source)).append('\n');
      for (String i : imports) {
        sb.append("import ").append(i).append(";\n");
      }
      sb.append('\n').append(cleanup(out.toString()));
      return sb.toString();
    }

    private static String cleanup(String s) {
      return s.replaceAll("\n\n+(\\s*})", "\n$1").replaceAll("\\{\n\n+", "{\n\n");
    }
  }
}
