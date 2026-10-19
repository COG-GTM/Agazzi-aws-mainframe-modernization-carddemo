package com.carddemo.copybook;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Zoned DISPLAY numerics with trailing sign overpunch, as found in the core VSAM files (plan §1.5):
 * positive last digit {@code {ABCDEFGHI} = +0..+9}, negative {@code }JKLMNOPQR} = -0..-9}.
 */
public final class ZonedDecimal {

  private static final String POSITIVE = "{ABCDEFGHI";
  private static final String NEGATIVE = "}JKLMNOPQR";

  private ZonedDecimal() {}

  /** Decodes {@code text} ({@code digits} characters) into a BigDecimal with {@code scale}. */
  public static BigDecimal decode(String text, int scale, boolean signed) {
    if (text.isEmpty()) {
      throw new IllegalArgumentException("empty zoned field");
    }
    char last = text.charAt(text.length() - 1);
    boolean negative = false;
    char digit = last;
    if (signed) {
      int p = POSITIVE.indexOf(last);
      int n = NEGATIVE.indexOf(last);
      if (p >= 0) {
        digit = (char) ('0' + p);
      } else if (n >= 0) {
        digit = (char) ('0' + n);
        negative = true;
      }
    }
    String digits = (text.substring(0, text.length() - 1) + digit).replace(' ', '0');
    for (int i = 0; i < digits.length(); i++) {
      if (!Character.isDigit(digits.charAt(i))) {
        throw new IllegalArgumentException("invalid zoned decimal: '" + text + "'");
      }
    }
    BigInteger unscaled = new BigInteger(digits);
    return new BigDecimal(negative ? unscaled.negate() : unscaled, scale);
  }

  /**
   * Encodes {@code value} into {@code digits} characters. High-order digits beyond the PIC are
   * truncated like a COBOL MOVE (plan §3.4 rule 3); low-order digits beyond the scale are
   * truncated.
   */
  public static String encode(BigDecimal value, int digits, int scale, boolean signed) {
    BigInteger unscaled = value.setScale(scale, java.math.RoundingMode.DOWN).unscaledValue();
    boolean negative = unscaled.signum() < 0;
    String s = unscaled.abs().toString();
    if (s.length() > digits) {
      s = s.substring(s.length() - digits);
    }
    StringBuilder sb = new StringBuilder("0".repeat(digits - s.length())).append(s);
    if (signed) {
      int d = sb.charAt(digits - 1) - '0';
      sb.setCharAt(digits - 1, (negative ? NEGATIVE : POSITIVE).charAt(d));
    }
    return sb.toString();
  }
}
