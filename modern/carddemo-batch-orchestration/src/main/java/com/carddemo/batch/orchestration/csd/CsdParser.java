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

  public static List<CsdDefinition> parse(List<String> lines, String hlq) {
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
    List<CsdDefinition> defs = new ArrayList<>();
    for (String s : statements) {
      if (s.startsWith("DEFINE")) {
        defs.add(define(s.substring("DEFINE".length())));
      }
    }
    return defs;
  }

  private static CsdDefinition define(String body) {
    Map<String, String> kw = new LinkedHashMap<>();
    Matcher m = KEYWORD.matcher(body);
    int end = 0;
    while (m.find()) {
      kw.put(m.group(1), m.group(2).strip());
      end = m.end();
    }
    if (!body.substring(end).isBlank() || kw.isEmpty()) {
      throw new CsdSyntaxException(0, "malformed DEFINE: " + body.strip());
    }
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
