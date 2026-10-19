package com.carddemo.batch.reports.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DFSORT subset used by TRANREPT/CREASTMT: {@code INCLUDE COND=} on character fields, {@code SORT
 * FIELDS=(p,l,CH,A|D)} (stable) and {@code OUTREC FIELDS=}. Positions are 1-based as in the control
 * statements.
 */
public final class RecordSort {

  /** {@code INCLUDE} condition {@code (start,length,CH,op,C'value')}. */
  public record Include(int start, int length, String op, String value) {
    boolean test(String record) {
      int c = slice(record, start, length).compareTo(value);
      return switch (op) {
        case "GE" -> c >= 0;
        case "LE" -> c <= 0;
        case "GT" -> c > 0;
        case "LT" -> c < 0;
        case "EQ" -> c == 0;
        case "NE" -> c != 0;
        default -> throw new IllegalArgumentException("Unsupported INCLUDE operator " + op);
      };
    }
  }

  /** {@code SORT FIELDS} key {@code (start,length,CH,A|D)}. */
  public record Key(int start, int length, boolean ascending) {}

  /** {@code OUTREC} item: {@code length} bytes from {@code inputStart} to {@code outputStart}. */
  public record Outrec(int outputStart, int inputStart, int length) {}

  private RecordSort() {}

  public static List<String> sort(
      List<String> records,
      List<Include> includes,
      List<Key> keys,
      List<Outrec> outrec,
      int outputLrecl) {
    List<String> out = new ArrayList<>();
    for (String r : records) {
      if (includes.stream().allMatch(i -> i.test(r))) {
        out.add(r);
      }
    }
    Comparator<String> cmp = (a, b) -> 0;
    for (Key k : keys) {
      Comparator<String> byKey = Comparator.comparing(r -> slice(r, k.start(), k.length()));
      cmp = cmp.thenComparing(k.ascending() ? byKey : byKey.reversed());
    }
    out.sort(cmp);
    if (outrec.isEmpty()) {
      return out;
    }
    List<String> reformatted = new ArrayList<>(out.size());
    for (String r : out) {
      char[] buf = " ".repeat(outputLrecl).toCharArray();
      for (Outrec o : outrec) {
        String part = slice(r, o.inputStart(), o.length());
        part.getChars(0, part.length(), buf, o.outputStart() - 1);
      }
      reformatted.add(new String(buf));
    }
    return reformatted;
  }

  static String slice(String record, int start, int length) {
    return FixedRecords.pad(
        record.length() >= start - 1 ? record.substring(start - 1) : "", length);
  }
}
