package com.carddemo.platform.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * COBOL arithmetic semantics (plan §3.4): COMPUTE without ROUNDED truncates ({@link
 * RoundingMode#DOWN}), with ROUNDED uses {@link RoundingMode#HALF_UP}; a numeric MOVE to a smaller
 * PIC drops high-order digits ({@link #fit}). Every operation states its mode explicitly.
 */
public final class CobolMath {

  /** COBOL rounding phrase of a statement. */
  public enum Mode {
    TRUNCATE(RoundingMode.DOWN),
    ROUNDED(RoundingMode.HALF_UP);

    private final RoundingMode rounding;

    Mode(RoundingMode rounding) {
      this.rounding = rounding;
    }

    public RoundingMode rounding() {
      return rounding;
    }
  }

  private CobolMath() {}

  /**
   * Stores {@code value} into {@code PIC S9(integerDigits)V9(scale)}: scale then high-order fit.
   */
  public static BigDecimal store(BigDecimal value, int integerDigits, int scale, Mode mode) {
    return fit(value.setScale(scale, mode.rounding()), integerDigits, scale);
  }

  /** MOVE semantics: keeps the low-order {@code integerDigits} integer digits (no SIZE ERROR). */
  public static BigDecimal fit(BigDecimal value, int integerDigits, int scale) {
    BigDecimal scaled = value.setScale(scale, RoundingMode.DOWN);
    BigInteger unscaled = scaled.unscaledValue();
    BigInteger modulus = BigInteger.TEN.pow(integerDigits + scale);
    BigInteger kept = unscaled.abs().mod(modulus);
    return new BigDecimal(unscaled.signum() < 0 ? kept.negate() : kept, scale);
  }

  public static BigDecimal add(BigDecimal a, BigDecimal b, int integerDigits, int scale, Mode m) {
    return store(a.add(b), integerDigits, scale, m);
  }

  public static BigDecimal subtract(
      BigDecimal a, BigDecimal b, int integerDigits, int scale, Mode m) {
    return store(a.subtract(b), integerDigits, scale, m);
  }

  public static BigDecimal multiply(
      BigDecimal a, BigDecimal b, int integerDigits, int scale, Mode m) {
    return store(a.multiply(b), integerDigits, scale, m);
  }

  /** Division keeps 18 extra intermediate decimals (enough for the CardDemo PICs) then stores. */
  public static BigDecimal divide(
      BigDecimal a, BigDecimal b, int integerDigits, int scale, Mode m) {
    BigDecimal q = a.divide(b, scale + 18, RoundingMode.DOWN);
    return store(q, integerDigits, scale, m);
  }

  /** {@code ON SIZE ERROR} check: true when {@code value} does not fit the PIC. */
  public static boolean sizeError(BigDecimal value, int integerDigits) {
    return value.abs().setScale(0, RoundingMode.DOWN).precision() > integerDigits
        && value.abs().compareTo(BigDecimal.ONE) >= 0;
  }
}
