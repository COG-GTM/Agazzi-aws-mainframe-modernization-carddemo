package com.carddemo.ext.trantype.store;

/** PIC X helpers: domain values stay padded (CONVENTIONS), presentation trims. */
public final class Pic {

  public static final int TYPE_LEN = 2;
  public static final int DESC_LEN = 50;
  public static final String TYPE_FILLER = "00000000";

  private Pic() {}

  public static String pad(String s, int len) {
    String v = s == null ? "" : s;
    if (v.length() >= len) {
      return v.substring(0, len);
    }
    return v + " ".repeat(len - v.length());
  }

  public static String rtrim(String s) {
    if (s == null) {
      return "";
    }
    int end = s.length();
    while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\0')) {
      end--;
    }
    return s.substring(0, end);
  }

  public static boolean isBlank(String s) {
    return s == null || s.replace('\0', ' ').isBlank();
  }
}
