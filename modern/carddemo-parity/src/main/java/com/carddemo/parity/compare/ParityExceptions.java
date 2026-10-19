package com.carddemo.parity.compare;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registered deviations of {@code modern/PARITY_EXCEPTIONS.md} (plan §11.6). Table rows: {@code |
 * ID | program/job | field | COBOL | Java | reason | approver |}; comparators ignore only these.
 */
public final class ParityExceptions {

  /** One registered deviation; {@code scope} and {@code field} accept {@code *}. */
  public record Entry(String id, String scope, String field) {
    boolean covers(FieldDiff d) {
      return (scope.equals("*") || scope.equalsIgnoreCase(d.job()))
          && (field.equals("*") || field.equalsIgnoreCase(d.field()));
    }
  }

  private final List<Entry> entries;

  public ParityExceptions(List<Entry> entries) {
    this.entries = List.copyOf(entries);
  }

  public static ParityExceptions none() {
    return new ParityExceptions(List.of());
  }

  public static ParityExceptions load(Path markdown) throws IOException {
    List<Entry> list = new ArrayList<>();
    if (Files.exists(markdown)) {
      for (String line : Files.readAllLines(markdown)) {
        String[] c = line.split("\\|");
        if (c.length > 4 && c[1].trim().toUpperCase(Locale.ROOT).matches("PE-\\d+")) {
          list.add(new Entry(c[1].trim(), c[2].trim(), c[3].trim().replace("`", "")));
        }
      }
    }
    return new ParityExceptions(list);
  }

  public List<Entry> entries() {
    return entries;
  }

  public boolean isRegistered(FieldDiff d) {
    return entries.stream().anyMatch(e -> e.covers(d));
  }

  public List<FieldDiff> unregistered(List<FieldDiff> diffs) {
    return diffs.stream().filter(d -> !isRegistered(d)).toList();
  }
}
