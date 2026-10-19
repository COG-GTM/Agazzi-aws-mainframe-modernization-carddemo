package com.carddemo.batch.datalifecycle.cobol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Byte-level helpers that reproduce how the legacy programs (as executed by the golden harness,
 * GnuCOBOL {@code -std=ibm}) lay out DISPLAY, COMP-3 and COMP fields.
 *
 * <p>Zoned values read from the sample data are decoded with mainframe overpunch semantics ({@code
 * {A-I}} positive, {@code }J-R} negative, plus the ASCII {@code p-y} negative convention). Values
 * produced by a numeric conversion are written in the native ASCII convention: plain digits for
 * positive values, {@code 0x70+digit} in the last byte for negative ones.
 */
public final class CobolData {

  private CobolData() {}

  public static byte[] spaces(int length) {
    byte[] b = new byte[length];
    Arrays.fill(b, (byte) ' ');
    return b;
  }

  public static String text(byte[] rec, int off, int len) {
    return new String(rec, off, len, StandardCharsets.ISO_8859_1);
  }

  public static void putText(byte[] rec, int off, int len, String value) {
    byte[] src = value.getBytes(StandardCharsets.ISO_8859_1);
    for (int i = 0; i < len; i++) {
      rec[off + i] = i < src.length ? src[i] : (byte) ' ';
    }
  }

  public static void copy(byte[] src, int srcOff, byte[] dst, int dstOff, int len) {
    System.arraycopy(src, srcOff, dst, dstOff, len);
  }

  /** Decodes a zoned DISPLAY field. Invalid digits contribute their low nibble, like COBOL. */
  public static BigDecimal zoned(byte[] rec, int off, int len, int scale) {
    BigInteger unscaled = BigInteger.ZERO;
    boolean negative = false;
    for (int i = 0; i < len; i++) {
      int c = rec[off + i] & 0xFF;
      int digit;
      if (i == len - 1 && !(c >= '0' && c <= '9')) {
        if (c == '{') {
          digit = 0;
        } else if (c >= 'A' && c <= 'I') {
          digit = c - 'A' + 1;
        } else if (c == '}') {
          digit = 0;
          negative = true;
        } else if (c >= 'J' && c <= 'R') {
          digit = c - 'J' + 1;
          negative = true;
        } else if (c >= 'p' && c <= 'y') {
          digit = c - 'p';
          negative = true;
        } else {
          digit = c & 0x0F;
        }
      } else {
        digit = (c & 0x0F) % 10;
      }
      unscaled = unscaled.multiply(BigInteger.TEN).add(BigInteger.valueOf(digit));
    }
    BigDecimal value = new BigDecimal(unscaled, scale);
    return negative ? value.negate() : value;
  }

  /** Unscaled digits of {@code value} truncated (COBOL MOVE) to {@code digits} positions. */
  private static String digits(BigDecimal value, int digits, int scale) {
    BigInteger unscaled = value.setScale(scale, RoundingMode.DOWN).unscaledValue().abs();
    String s = unscaled.toString();
    if (s.length() > digits) {
      s = s.substring(s.length() - digits);
    }
    return "0".repeat(digits - s.length()) + s;
  }

  /** Writes a zoned DISPLAY field in the native ASCII sign convention. */
  public static void putZoned(
      byte[] rec, int off, BigDecimal value, int digits, int scale, boolean signed) {
    byte[] d = digits(value, digits, scale).getBytes(StandardCharsets.ISO_8859_1);
    if (signed && value.signum() < 0) {
      d[digits - 1] = (byte) (0x70 + (d[digits - 1] - '0'));
    }
    System.arraycopy(d, 0, rec, off, digits);
  }

  /** Writes a COMP-3 field of {@code digits} digits ({@code digits / 2 + 1} bytes). */
  public static void putPacked(
      byte[] rec, int off, BigDecimal value, int digits, int scale, boolean signed) {
    int bytes = digits / 2 + 1;
    String d = digits(value, bytes * 2 - 1, scale);
    int sign = !signed ? 0x0F : value.signum() < 0 ? 0x0D : 0x0C;
    for (int i = 0; i < bytes; i++) {
      int hi = d.charAt(i * 2) - '0';
      int lo = i == bytes - 1 ? sign : d.charAt(i * 2 + 1) - '0';
      rec[off + i] = (byte) ((hi << 4) | lo);
    }
  }

  public static BigDecimal packed(byte[] rec, int off, int bytes, int scale) {
    BigInteger unscaled = BigInteger.ZERO;
    for (int i = 0; i < bytes; i++) {
      int b = rec[off + i] & 0xFF;
      unscaled = unscaled.multiply(BigInteger.TEN).add(BigInteger.valueOf((b >> 4) % 10));
      if (i < bytes - 1) {
        unscaled = unscaled.multiply(BigInteger.TEN).add(BigInteger.valueOf((b & 0x0F) % 10));
      }
    }
    int sign = rec[off + bytes - 1] & 0x0F;
    BigDecimal value = new BigDecimal(unscaled, scale);
    return sign == 0x0D || sign == 0x0B ? value.negate() : value;
  }

  /** Writes a big-endian two's-complement COMP field holding the unscaled value. */
  public static void putBinary(byte[] rec, int off, int bytes, BigDecimal value, int scale) {
    long v = value.setScale(scale, RoundingMode.DOWN).unscaledValue().longValue();
    for (int i = bytes - 1; i >= 0; i--) {
      rec[off + i] = (byte) v;
      v >>= 8;
    }
  }

  public static BigDecimal binary(byte[] rec, int off, int bytes, int scale, boolean signed) {
    long v = signed && rec[off] < 0 ? -1L : 0L;
    for (int i = 0; i < bytes; i++) {
      v = (v << 8) | (rec[off + i] & 0xFF);
    }
    return BigDecimal.valueOf(v, scale);
  }

  /** DISPLAY of a signed zoned item: the digits followed by the separate sign. */
  public static String displaySigned(BigDecimal value, int digits, int scale) {
    return digits(value, digits, scale) + (value.signum() < 0 ? "-" : "+");
  }
}
