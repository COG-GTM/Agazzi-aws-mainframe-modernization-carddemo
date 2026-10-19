package com.carddemo.ext.authorization.web;

/** STRING ... DELIMITED BY helpers for the CPVS customer block. */
final class AuthorizationScreenSupport {
  private AuthorizationScreenSupport() {}

  static String padRight(String v, int len) {
    return ScreenFormat.AuthTexts.pad(v, len);
  }

  static String cut(String v, int len) {
    String s = v == null ? "" : v;
    return s.length() > len ? s.substring(0, len) : s;
  }

  static String untilSpace(String v) {
    String s = v == null ? "" : v;
    int i = s.indexOf(' ');
    return i < 0 ? s : s.substring(0, i);
  }

  static String untilDoubleSpace(String v) {
    String s = v == null ? "" : v;
    int i = s.indexOf("  ");
    return i < 0 ? s : s.substring(0, i);
  }

  /** first DELIMITED BY SPACES ' ' middle(1:1) ' ' last DELIMITED BY SPACES, into X(25). */
  static String name(String first, String middle, String last) {
    String m = padRight(middle, 1).substring(0, 1);
    return cut(untilSpace(first) + ' ' + m + ' ' + untilSpace(last), 25);
  }
}
