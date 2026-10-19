package com.carddemo.batch.reports.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-length datasets stored as text: one record per line, {@code LRECL} bytes followed by {@code
 * \n} (same format as the GnuCOBOL golden harness). Records are handled as ISO-8859-1 strings so
 * that one char is one byte.
 */
public final class FixedRecords {

  public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

  private FixedRecords() {}

  /** Reads a line-oriented dataset, padding/truncating every non-empty line to {@code lrecl}. */
  public static List<String> read(Path file, int lrecl) {
    List<String> out = new ArrayList<>();
    if (!Files.exists(file)) {
      return out;
    }
    String data;
    try {
      data = Files.readString(file, CHARSET);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (String line : data.split("\n", -1)) {
      String l = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
      if (!l.isEmpty()) {
        out.add(pad(l, lrecl));
      }
    }
    return out;
  }

  /** Writes {@code records} (each padded/truncated to {@code lrecl}) one per line. */
  public static void write(Path file, List<String> records, int lrecl) {
    StringBuilder sb = new StringBuilder(records.size() * (lrecl + 1));
    for (String r : records) {
      sb.append(pad(r, lrecl)).append('\n');
    }
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(file, sb, CHARSET);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@code MOVE} of an alphanumeric value into a {@code PIC X(len)} field. */
  public static String pad(String value, int len) {
    String v = value == null ? "" : value;
    if (v.length() >= len) {
      return v.substring(0, len);
    }
    return v + " ".repeat(len - v.length());
  }
}
