package com.carddemo.online.transaction.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** PIC X / PIC 9 / edited-picture helpers reproducing the COBOL MOVE and class-test rules. */
public final class CobolFmt {

  private CobolFmt() {}

  /** {@code = SPACES OR LOW-VALUES}. */
  public static boolean blank(String s) {
    if (s == null) {
      return true;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != ' ' && c != '\0') {
        return false;
      }
    }
    return true;
  }

  /** MOVE to PIC X(n): left-justified, space padded, truncated on the right. */
  public static String pad(String s, int n) {
    String v = s == null ? "" : s;
    return v.length() >= n ? v.substring(0, n) : v + " ".repeat(n - v.length());
  }

  /** Class test {@code IS NUMERIC} on a PIC X field: every byte is a digit. */
  public static boolean isNumeric(String s) {
    if (s == null || s.isEmpty()) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) < '0' || s.charAt(i) > '9') {
        return false;
      }
    }
    return true;
  }

  /** Unsigned PIC 9(n) DISPLAY image of {@code v} (high-order digits truncated, as MOVE does). */
  public static String digits(long v, int n) {
    String s = Long.toString(Math.abs(v));
    return s.length() >= n ? s.substring(s.length() - n) : "0".repeat(n - s.length()) + s;
  }

  /**
   * Edited picture {@code +9(n).99}: explicit sign, zero-filled integer part (high-order digits
   * truncated), two decimals truncated.
   */
  public static String editSigned(BigDecimal v, int integerDigits) {
    BigDecimal x = v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.DOWN);
    String sign = x.signum() < 0 ? "-" : "+";
    String plain = x.abs().toPlainString();
    int dot = plain.indexOf('.');
    String ip = plain.substring(0, dot);
    String fp = plain.substring(dot + 1);
    ip = ip.length() >= integerDigits ? ip.substring(ip.length() - integerDigits) : ip;
    return sign + "0".repeat(integerDigits - ip.length()) + ip + "." + fp;
  }

  /**
   * FUNCTION NUMVAL-C: optional leading/trailing sign (+, -, CR, DB), optional {@code $}, commas,
   * decimal point. Invalid arguments return 0 (GnuCOBOL behaviour; undefined on z/OS).
   */
  public static BigDecimal numvalC(String s) {
    if (s == null) {
      return BigDecimal.ZERO;
    }
    String t = s.replace('\0', ' ').strip().replace("$", "").replace(",", "");
    boolean neg = false;
    if (t.startsWith("-") || t.startsWith("+")) {
      neg = t.startsWith("-");
      t = t.substring(1).strip();
    } else if (t.endsWith("-") || t.endsWith("+")) {
      neg = t.endsWith("-");
      t = t.substring(0, t.length() - 1).strip();
    } else if (t.endsWith("CR") || t.endsWith("DB")) {
      neg = true;
      t = t.substring(0, t.length() - 2).strip();
    }
    if (t.isEmpty() || !t.matches("\\d*(\\.\\d*)?") || t.equals(".")) {
      return BigDecimal.ZERO;
    }
    BigDecimal v = new BigDecimal(t.startsWith(".") ? "0" + t : t.endsWith(".") ? t + "0" : t);
    return neg ? v.negate() : v;
  }

  /** NUMVAL-C stored in an unsigned integer PIC 9(n) (decimals and high-order digits dropped). */
  public static String numvalToDigits(String s, int n) {
    BigDecimal v = numvalC(s).abs().setScale(0, RoundingMode.DOWN);
    String d = v.toPlainString();
    return d.length() >= n ? d.substring(d.length() - n) : "0".repeat(n - d.length()) + d;
  }

  /**
   * Alphanumeric to numeric MOVE (PIC X(n) to PIC 9(n)) of a terminal field: digits are kept and
   * blanks contribute a zero digit (zoned nibble of X'40'). Returns {@code null} when other
   * characters make the value unusable as a key.
   */
  public static Long zonedMove(String s) {
    if (s == null || s.isEmpty()) {
      return null;
    }
    long v = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      int d;
      if (c >= '0' && c <= '9') {
        d = c - '0';
      } else if (c == ' ' || c == '\0') {
        d = 0;
      } else {
        return null;
      }
      v = v * 10 + d;
    }
    return v;
  }

  /** Right-trimmed presentation value (plan §3.4 rule 4: trim only in the DTO). */
  public static String rtrim(String s) {
    if (s == null) {
      return null;
    }
    int end = s.length();
    while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\0')) {
      end--;
    }
    return s.substring(0, end);
  }
}
