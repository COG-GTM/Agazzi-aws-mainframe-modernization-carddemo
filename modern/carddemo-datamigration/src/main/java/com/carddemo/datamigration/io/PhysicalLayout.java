package com.carddemo.datamigration.io;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Physical shape of an extract, captured at import so that {@code relationalToVsam} can rebuild the
 * original file byte for byte (record separator, separator after the last record, trailing spaces
 * trimmed by the download tool).
 *
 * @param fixed records are concatenated with no separator (mainframe PS / RECFM=FB)
 * @param recordTerminator separator after every record but the last ({@code "\n"} or {@code
 *     "\r\n"})
 * @param finalTerminator separator after the last record ({@code ""} when the file has none)
 * @param rightTrimmed trailing spaces of each record were removed
 */
public record PhysicalLayout(
    boolean fixed, String recordTerminator, String finalTerminator, boolean rightTrimmed) {

  public static final PhysicalLayout FIXED = new PhysicalLayout(true, "", "", false);
  public static final PhysicalLayout LINES = new PhysicalLayout(false, "\n", "\n", false);

  /** Splits a fixed-length file. */
  public static List<byte[]> splitFixed(byte[] data, int lrecl) {
    if (data.length % lrecl != 0) {
      throw new MalformedSourceException(
          "file length " + data.length + " is not a multiple of LRECL " + lrecl);
    }
    List<byte[]> out = new ArrayList<>(data.length / lrecl);
    for (int off = 0; off < data.length; off += lrecl) {
      byte[] r = new byte[lrecl];
      System.arraycopy(data, off, r, 0, lrecl);
      out.add(r);
    }
    return out;
  }

  /** Result of {@link #splitLines}. */
  public record Lines(PhysicalLayout layout, List<byte[]> records) {}

  /** Splits a line-oriented ASCII file, padding short records to LRECL with spaces. */
  public static Lines splitLines(byte[] data, int lrecl) {
    List<byte[]> raw = new ArrayList<>();
    List<String> terms = new ArrayList<>();
    int start = 0;
    for (int i = 0; i < data.length; i++) {
      if (data[i] == '\n') {
        boolean cr = i > start && data[i - 1] == '\r';
        raw.add(slice(data, start, cr ? i - 1 : i));
        terms.add(cr ? "\r\n" : "\n");
        start = i + 1;
      }
    }
    if (start < data.length) {
      raw.add(slice(data, start, data.length));
      terms.add("");
    }
    String recordTerm = terms.size() > 1 ? terms.get(0) : "\n";
    for (int i = 0; i < terms.size() - 1; i++) {
      if (!terms.get(i).equals(recordTerm)) {
        throw new MalformedSourceException("mixed record separators at record " + (i + 1));
      }
    }
    String finalTerm = terms.isEmpty() ? "\n" : terms.get(terms.size() - 1);
    boolean shortSeen = false;
    boolean trailingSpaceSeen = false;
    List<byte[]> records = new ArrayList<>(raw.size());
    for (int i = 0; i < raw.size(); i++) {
      byte[] r = raw.get(i);
      if (r.length > lrecl) {
        throw new MalformedSourceException(
            "record " + (i + 1) + " is " + r.length + " bytes, LRECL is " + lrecl);
      }
      shortSeen |= r.length < lrecl;
      trailingSpaceSeen |= r.length > 0 && r[r.length - 1] == ' ';
      byte[] padded = new byte[lrecl];
      System.arraycopy(r, 0, padded, 0, r.length);
      for (int j = r.length; j < lrecl; j++) {
        padded[j] = ' ';
      }
      records.add(padded);
    }
    if (shortSeen && trailingSpaceSeen) {
      throw new MalformedSourceException(
          "records are partly right-trimmed: the file cannot be rebuilt byte-identical");
    }
    return new Lines(new PhysicalLayout(false, recordTerm, finalTerm, shortSeen), records);
  }

  /** Inverse of the split: the bytes of the whole file. */
  public byte[] join(List<byte[]> records) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 0; i < records.size(); i++) {
      byte[] r = records.get(i);
      int len = r.length;
      if (rightTrimmed) {
        while (len > 0 && r[len - 1] == ' ') {
          len--;
        }
      }
      out.write(r, 0, len);
      if (!fixed) {
        String t = i == records.size() - 1 ? finalTerminator : recordTerminator;
        out.writeBytes(t.getBytes(StandardCharsets.US_ASCII));
      }
    }
    return out.toByteArray();
  }

  /** Compact text form stored in {@code dm_dataset_layout.layout}. */
  public String encode() {
    if (fixed) {
      return "FIXED";
    }
    return "LINES;" + name(recordTerminator) + ";" + name(finalTerminator) + ";" + rightTrimmed;
  }

  public static PhysicalLayout decode(String s) {
    if (s == null || s.equals("FIXED")) {
      return FIXED;
    }
    String[] p = s.split(";", -1);
    return new PhysicalLayout(false, term(p[1]), term(p[2]), Boolean.parseBoolean(p[3]));
  }

  private static String name(String t) {
    return switch (t) {
      case "\r\n" -> "CRLF";
      case "\n" -> "LF";
      default -> "NONE";
    };
  }

  private static String term(String n) {
    return switch (n) {
      case "CRLF" -> "\r\n";
      case "LF" -> "\n";
      default -> "";
    };
  }

  private static byte[] slice(byte[] d, int from, int to) {
    byte[] r = new byte[to - from];
    System.arraycopy(d, from, r, 0, r.length);
    return r;
  }
}
