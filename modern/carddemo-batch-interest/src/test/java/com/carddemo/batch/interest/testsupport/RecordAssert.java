package com.carddemo.batch.interest.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.RecordCodec;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Field-level record comparison (the RecordComparator rules of carddemo-parity): numeric fields by
 * value, so GnuCOBOL's plain positive digits equal the z/OS overpunch {@code {..I}; text exact.
 */
public final class RecordAssert {

  private RecordAssert() {}

  public static List<String> diff(
      RecordCodec<? extends Record> codec, List<String> expected, List<String> actual) {
    List<String> diffs = new ArrayList<>();
    if (expected.size() != actual.size()) {
      diffs.add("record count expected " + expected.size() + " but was " + actual.size());
    }
    for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
      diffs.addAll(diff(codec, i + 1, expected.get(i), actual.get(i)));
    }
    return diffs;
  }

  static List<String> diff(RecordCodec<? extends Record> codec, int n, String e, String a) {
    List<String> diffs = new ArrayList<>();
    Record re = codec.decodeLine(e);
    Record ra = codec.decodeLine(a);
    for (RecordComponent c : re.getClass().getRecordComponents()) {
      try {
        Object ve = c.getAccessor().invoke(re);
        Object va = c.getAccessor().invoke(ra);
        boolean same =
            ve instanceof BigDecimal be && va instanceof BigDecimal ba
                ? be.compareTo(ba) == 0
                : Objects.equals(ve, va);
        if (!same) {
          diffs.add(
              "record %d field %s: expected <%s> but was <%s>".formatted(n, c.getName(), ve, va));
        }
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException(ex);
      }
    }
    return diffs;
  }

  public static void assertRecords(
      RecordCodec<? extends Record> codec, List<String> expected, List<String> actual) {
    assertThat(diff(codec, expected, actual)).as("field differences").isEmpty();
  }

  /** SYSOUT: exact lines, except TCATBALF DISPLAYs which are compared as records. */
  public static void assertSysout(
      RecordCodec<? extends Record> recordCodec, List<String> expected, List<String> actual) {
    List<String> diffs = new ArrayList<>();
    if (expected.size() != actual.size()) {
      diffs.add("line count expected " + expected.size() + " but was " + actual.size());
    }
    for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
      String e = expected.get(i);
      String a = actual.get(i);
      if (e.equals(a)) {
        continue;
      }
      if (e.length() == recordCodec.recordLength() && a.length() == recordCodec.recordLength()) {
        try {
          diffs.addAll(diff(recordCodec, i + 1, e, a));
          continue;
        } catch (IllegalArgumentException ignored) {
          // not a record line
        }
      }
      diffs.add("line %d: expected <%s> but was <%s>".formatted(i + 1, e, a));
    }
    assertThat(diffs).as("SYSOUT differences").isEmpty();
  }
}
