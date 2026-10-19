package com.carddemo.parity.compare;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.copybook.ZonedRecordCodec;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Field-level comparison of fixed-length record files using the copybook layout (plan §11.3). For
 * layouts without a Fase 0 codec the whole record is compared ({@code field = RECORD}).
 */
public final class RecordComparator {

  private final ParityExceptions exceptions;

  public RecordComparator(ParityExceptions exceptions) {
    this.exceptions = exceptions;
  }

  /** Reads a golden text file (records of LRECL terminated by newline), padding each record. */
  public static List<String> readRecords(Path file, int lrecl) throws IOException {
    String text = Files.readString(file, StandardCharsets.ISO_8859_1);
    if (text.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String l : text.split("\n", -1)) {
      out.add(l.length() < lrecl ? l + " ".repeat(lrecl - l.length()) : l);
    }
    if (out.get(out.size() - 1).isBlank() && text.endsWith("\n")) {
      out.remove(out.size() - 1);
    }
    return out;
  }

  public List<FieldDiff> compareFiles(
      String job, String file, String layout, int lrecl, Path expected, Path actual)
      throws IOException {
    return compare(job, file, layout, readRecords(expected, lrecl), readRecords(actual, lrecl));
  }

  /** Compares two record lists; returns only diffs not registered in PARITY_EXCEPTIONS. */
  public List<FieldDiff> compare(
      String job, String file, String layout, List<String> expected, List<String> actual) {
    List<FieldDiff> diffs = new ArrayList<>();
    if (expected.size() != actual.size()) {
      diffs.add(
          new FieldDiff(
              job,
              file,
              0,
              "RECORD-COUNT",
              String.valueOf(expected.size()),
              String.valueOf(actual.size())));
    }
    ZonedRecordCodec<?> codec = codecFor(layout);
    for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
      String e = expected.get(i);
      String a = actual.get(i);
      if (e.equals(a)) {
        continue;
      }
      if (codec == null
          || e.length() != codec.recordLength()
          || a.length() != codec.recordLength()) {
        diffs.add(new FieldDiff(job, file, i + 1, "RECORD", e, a));
        continue;
      }
      diffs.addAll(compareRecord(job, file, i + 1, codec, e, a));
    }
    return exceptions.unregistered(diffs);
  }

  private static List<FieldDiff> compareRecord(
      String job, String file, int n, ZonedRecordCodec<?> codec, String e, String a) {
    List<FieldDiff> out = new ArrayList<>();
    for (ZonedRecordCodec.FieldLayout f : codec.fields()) {
      String ev = e.substring(f.offset(), f.offset() + f.length());
      String av = a.substring(f.offset(), f.offset() + f.length());
      if (!Objects.equals(ev, av) && !sameNumber(f, ev, av)) {
        out.add(
            new FieldDiff(job, file, n, f.name(), display(codec, f, ev), display(codec, f, av)));
      }
    }
    return out;
  }

  /**
   * Numeric fields compare by value: GnuCOBOL writes positive signed DISPLAY with a plain digit
   * (sign nibble F) where z/OS samples use overpunch {@code {..I} (C); both mean the same number.
   */
  private static boolean sameNumber(ZonedRecordCodec.FieldLayout f, String e, String a) {
    if (!f.numeric()) {
      return false;
    }
    try {
      return com.carddemo.copybook.ZonedDecimal.decode(e, f.scale(), f.signed())
              .compareTo(com.carddemo.copybook.ZonedDecimal.decode(a, f.scale(), f.signed()))
          == 0;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private static String display(RecordCodec<?> codec, ZonedRecordCodec.FieldLayout f, String raw) {
    if (!f.numeric()) {
      return raw;
    }
    try {
      return com.carddemo.copybook.ZonedDecimal.decode(raw, f.scale(), f.signed()).toPlainString();
    } catch (IllegalArgumentException ex) {
      return raw;
    }
  }

  private static ZonedRecordCodec<?> codecFor(String layout) {
    if (layout == null) {
      return null;
    }
    return CoreCodecs.all().containsKey(layout)
        ? (ZonedRecordCodec<?>) CoreCodecs.forCopybook(layout)
        : null;
  }

  /** Names of the record components of a layout (for reports). */
  public static List<String> fieldNames(String layout) {
    ZonedRecordCodec<?> c = codecFor(layout);
    return c == null
        ? List.of("RECORD")
        : Arrays.stream(c.recordType().getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
  }
}
