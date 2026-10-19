package com.carddemo.online.auth.service;

/** PIC X helpers for BMS fields (plan §3.4 rule 4: padding kept inside, trimmed in DTOs). */
public final class ScreenText {

  private ScreenText() {}

  /**
   * COBOL {@code = SPACES OR LOW-VALUES} on a received map field ({@code null} = not sent): the
   * whole field is all spaces or all low-values; a mix of the two is not blank.
   */
  public static boolean isBlank(String s) {
    if (s == null || s.isEmpty()) {
      return true;
    }
    return s.chars().allMatch(c -> c == ' ') || s.chars().allMatch(c -> c == 0);
  }

  /** MOVE into PIC X(n): truncate on the right, pad with spaces. */
  public static String picX(String s, int n) {
    String v = s == null ? "" : s;
    return v.length() >= n ? v.substring(0, n) : v + " ".repeat(n - v.length());
  }

  /** Map output value for the presentation DTO: trailing padding removed. */
  public static String out(String s) {
    return s == null ? null : s.stripTrailing();
  }

  /** WS-MESSAGE (PIC X(80)) moved to ERRMSGO (PIC X(78)). */
  public static String errmsg(String message) {
    return message == null ? "" : out(picX(message, 78));
  }
}
