package com.carddemo.batch.reports.support;

/** {@code STRING ... DELIMITED BY} helpers. */
public final class CobolText {

  private CobolText() {}

  /** Part of {@code value} preceding the first occurrence of {@code delimiter}. */
  public static String delimitedBy(String value, String delimiter) {
    int i = value.indexOf(delimiter);
    return i < 0 ? value : value.substring(0, i);
  }

  /**
   * {@code STRING ... INTO target} on a target pre-filled with spaces: the concatenation is
   * truncated at the target length (overflow) and the rest of the target stays blank.
   */
  public static String stringInto(int targetLength, String... parts) {
    return FixedRecords.pad(String.join("", parts), targetLength);
  }
}
