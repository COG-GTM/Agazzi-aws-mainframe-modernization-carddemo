package com.carddemo.online.account.support;

import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** COBOL alphanumeric/numeric helpers used by the O2 paragraphs (PIC X padding, NUMVAL-C, edit). */
public final class CobolText {

  private static final Pattern NUMVAL_C =
      Pattern.compile(
          "^ *(?:([+-]) *)?(?:\\$ *)?([0-9,]*)(?:\\.([0-9]*))? *(?:([+-]|CR|DB) *)?$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern NUMVAL =
      Pattern.compile("^ *(?:([+-]) *)?([0-9]*)(?:\\.([0-9]*))? *(?:([+-]|CR|DB) *)?$");

  private CobolText() {}

  /** {@code SPACES} or {@code LOW-VALUES} (also {@code null}/empty from the REST layer). */
  public static boolean isBlank(String s) {
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

  /** MOVE to {@code PIC X(n)}: left-justified, truncated or padded with spaces. */
  public static String pad(String s, int n) {
    String v = s == null ? "" : s;
    if (v.length() >= n) {
      return v.substring(0, n);
    }
    return v + " ".repeat(n - v.length());
  }

  /** BMS {@code JUSTIFY=(RIGHT)} input field: right-justified, padded on the left with blanks. */
  public static String rightJustify(String s, int n) {
    String v = s == null ? "" : s.stripTrailing();
    if (v.length() >= n) {
      return v.substring(v.length() - n);
    }
    return " ".repeat(n - v.length()) + v;
  }

  /** Class test {@code IS NUMERIC} on {@code PIC X(n)}: every byte is a digit. */
  public static boolean isNumeric(String s) {
    if (s == null || s.isEmpty()) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  /** {@code FUNCTION TEST-NUMVAL-C(s) = 0}. */
  public static boolean testNumvalC(String s) {
    return parse(NUMVAL_C, s) != null;
  }

  /** {@code FUNCTION NUMVAL-C(s)}; {@code null} when the argument is not valid. */
  public static BigDecimal numvalC(String s) {
    return parse(NUMVAL_C, s);
  }

  /** {@code FUNCTION TEST-NUMVAL(s) = 0}. */
  public static boolean testNumval(String s) {
    return parse(NUMVAL, s) != null;
  }

  /** {@code FUNCTION NUMVAL(s)}; {@code null} when the argument is not valid. */
  public static BigDecimal numval(String s) {
    return parse(NUMVAL, s);
  }

  private static BigDecimal parse(Pattern p, String s) {
    if (s == null) {
      return null;
    }
    Matcher m = p.matcher(s.replace('\0', ' '));
    if (!m.matches()) {
      return null;
    }
    String intPart = m.group(2).replace(",", "");
    String frac = m.group(3) == null ? "" : m.group(3);
    if (intPart.isEmpty() && frac.isEmpty()) {
      return null;
    }
    if (m.group(1) != null && m.group(4) != null) {
      return null;
    }
    BigDecimal v = new BigDecimal((intPart.isEmpty() ? "0" : intPart) + "." + frac + "0");
    String sign = m.group(1) != null ? m.group(1) : m.group(4);
    boolean negative =
        sign != null
            && ("-".equals(sign) || "CR".equalsIgnoreCase(sign) || "DB".equalsIgnoreCase(sign));
    return negative ? v.negate() : v;
  }

  /** {@code COMPUTE x = NUMVAL-C(..)} into {@code PIC S9(10)V99} (no ROUNDED: truncation). */
  public static BigDecimal toS9v2(BigDecimal v) {
    return CobolMath.store(v, 10, 2, CobolMath.Mode.TRUNCATE);
  }

  /** MOVE to the BMS output picture {@code +ZZZ,ZZZ,ZZZ.99} (PICOUT of the amount fields). */
  public static String formatAmount(BigDecimal value) {
    BigDecimal v = CobolMath.fit(value, 9, 2);
    String digits = v.abs().setScale(2, RoundingMode.DOWN).unscaledValue().toString();
    digits = "0".repeat(11 - digits.length()) + digits;
    StringBuilder out = new StringBuilder(v.signum() < 0 ? "-" : "+");
    boolean significant = false;
    for (int i = 0; i < 9; i++) {
      char d = digits.charAt(i);
      if (i > 0 && i % 3 == 0) {
        out.append(significant ? ',' : ' ');
      }
      significant |= d != '0';
      out.append(significant ? d : ' ');
    }
    out.append('.').append(digits, 9, 11);
    return out.toString();
  }

  /** {@code FUNCTION UPPER-CASE(FUNCTION TRIM(s))}. */
  public static String upperTrim(String s) {
    return s == null ? "" : s.replace('\0', ' ').strip().toUpperCase(Locale.ROOT);
  }

  /** Presentation trim of a {@code PIC X} value ({@code LOW-VALUES} → {@code null}). */
  public static String display(String s) {
    if (s == null) {
      return null;
    }
    String v = s.replace('\0', ' ').stripTrailing();
    return v.isEmpty() ? null : v;
  }

  /** Unsigned DISPLAY numeric {@code PIC 9(n)} as text (leading zeros, low-order digits). */
  public static String zoned(long value, int n) {
    String d = Long.toString(Math.abs(value));
    if (d.length() >= n) {
      return d.substring(d.length() - n);
    }
    return "0".repeat(n - d.length()) + d;
  }
}
