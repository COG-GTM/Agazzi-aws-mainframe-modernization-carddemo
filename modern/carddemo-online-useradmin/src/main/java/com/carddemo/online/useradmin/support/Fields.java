package com.carddemo.online.useradmin.support;

import java.util.Locale;

/** BMS/PIC X field helpers: SPACES/LOW-VALUES tests, truncation and right padding. */
public final class Fields {

  private Fields() {}

  /** {@code field = SPACES OR LOW-VALUES}. */
  public static boolean isBlank(String value) {
    if (value == null) {
      return true;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c != ' ' && c != '\u0000') {
        return false;
      }
    }
    return true;
  }

  /** Value as received from a BMS field of {@code length} bytes (null = LOW-VALUES). */
  public static String received(String value, int length) {
    if (value == null) {
      return "";
    }
    String v = value.replace('\u0000', ' ');
    return v.length() > length ? v.substring(0, length) : v;
  }

  /** {@code MOVE value TO PIC X(length)}: truncate or pad with spaces. */
  public static String pad(String value, int length) {
    String v = value == null ? "" : value;
    if (v.length() >= length) {
      return v.substring(0, length);
    }
    return v + " ".repeat(length - v.length());
  }

  /** Trailing spaces removed, as rendered in JSON screen output. */
  public static String rtrim(String value) {
    if (value == null) {
      return "";
    }
    int end = value.length();
    while (end > 0 && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\u0000')) {
      end--;
    }
    return value.substring(0, end);
  }

  /** {@code STRING value DELIMITED BY SPACE}. */
  public static String delimitedBySpace(String value) {
    if (value == null) {
      return "";
    }
    int idx = value.indexOf(' ');
    return idx < 0 ? value : value.substring(0, idx);
  }

  /** Upper-cased, used for comparisons where COBOL accepts both cases. */
  public static String upper(String value) {
    return value == null ? "" : value.toUpperCase(Locale.ROOT);
  }
}
