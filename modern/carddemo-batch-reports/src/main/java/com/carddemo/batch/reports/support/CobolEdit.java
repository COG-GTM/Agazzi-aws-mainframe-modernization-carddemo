package com.carddemo.batch.reports.support;

import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;

/** COBOL numeric-edited MOVE semantics for the PICTUREs used by CBTRN03C and CBSTM03A. */
public final class CobolEdit {

  private CobolEdit() {}

  /** {@code PIC -ZZZ,ZZZ,ZZZ.ZZ} (detail amount). */
  public static String minusZzz(BigDecimal value) {
    return groupedWithLeadingSign(value, false);
  }

  /** {@code PIC +ZZZ,ZZZ,ZZZ.ZZ} (page/account/grand totals). */
  public static String plusZzz(BigDecimal value) {
    return groupedWithLeadingSign(value, true);
  }

  /** {@code PIC Z(9).99-} (statement amounts). */
  public static String zeroSuppressedTrailingSign(BigDecimal value) {
    BigDecimal v = CobolMath.fit(value, 9, 2);
    String digits = digits(v, 11);
    StringBuilder sb = new StringBuilder();
    boolean significant = false;
    for (int i = 0; i < 9; i++) {
      char c = digits.charAt(i);
      significant |= c != '0';
      sb.append(significant ? c : ' ');
    }
    sb.append('.').append(digits, 9, 11);
    sb.append(v.signum() < 0 ? '-' : ' ');
    return sb.toString();
  }

  /** {@code PIC 9(9).99-} (statement current balance). */
  public static String zeroFilledTrailingSign(BigDecimal value) {
    BigDecimal v = CobolMath.fit(value, 9, 2);
    String digits = digits(v, 11);
    return digits.substring(0, 9) + "." + digits.substring(9) + (v.signum() < 0 ? '-' : ' ');
  }

  private static String groupedWithLeadingSign(BigDecimal value, boolean plus) {
    BigDecimal v = CobolMath.fit(value, 9, 2);
    if (v.signum() == 0) {
      return " ".repeat(15);
    }
    String digits = digits(v, 11);
    StringBuilder sb = new StringBuilder();
    sb.append(v.signum() < 0 ? '-' : plus ? '+' : ' ');
    boolean significant = false;
    for (int i = 0; i < 9; i++) {
      if (i == 3 || i == 6) {
        sb.append(significant ? ',' : ' ');
      }
      char c = digits.charAt(i);
      significant |= c != '0';
      sb.append(significant ? c : ' ');
    }
    sb.append('.').append(digits, 9, 11);
    return sb.toString();
  }

  private static String digits(BigDecimal v, int width) {
    String s = v.abs().setScale(2).unscaledValue().toString();
    return "0".repeat(Math.max(0, width - s.length())) + s;
  }
}
