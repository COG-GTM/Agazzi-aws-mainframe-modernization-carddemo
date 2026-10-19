package com.carddemo.copybook.codec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** {@code COMP-3} packed decimal: two digits per byte, sign in the low nibble of the last byte. */
public final class PackedDecimal {

  private PackedDecimal() {}

  /** Bytes needed for {@code digits} digits. */
  public static int length(int digits) {
    return digits / 2 + 1;
  }

  /** Whether the bytes are a well-formed packed decimal (digit nibbles 0-9, sign nibble A-F). */
  public static boolean valid(byte[] b, int off, int len) {
    for (int i = 0; i < len; i++) {
      int hi = (b[off + i] >> 4) & 0x0F;
      int lo = b[off + i] & 0x0F;
      if (hi > 9 || (i < len - 1 ? lo > 9 : lo < 0x0A)) {
        return false;
      }
    }
    return true;
  }

  /** Decodes {@code len} bytes at {@code off}. Sign nibbles {@code B}/{@code D} are negative. */
  public static BigDecimal decode(byte[] b, int off, int len, int scale) {
    if (!valid(b, off, len)) {
      throw new IllegalArgumentException("invalid packed decimal " + hex(b, off, len));
    }
    StringBuilder digits = new StringBuilder(len * 2);
    for (int i = 0; i < len; i++) {
      digits.append((char) ('0' + ((b[off + i] >> 4) & 0x0F)));
      if (i < len - 1) {
        digits.append((char) ('0' + (b[off + i] & 0x0F)));
      }
    }
    int sign = b[off + len - 1] & 0x0F;
    BigInteger unscaled = new BigInteger(digits.toString());
    if (sign == 0x0B || sign == 0x0D) {
      unscaled = unscaled.negate();
    }
    return new BigDecimal(unscaled, scale);
  }

  /**
   * Encodes {@code value} into {@code length(digits)} bytes at {@code off}: scale truncated, high
   * order digits beyond the PIC truncated (COBOL MOVE), sign {@code C}/{@code D} when signed,
   * {@code F} when unsigned (absolute value).
   */
  public static void encode(
      BigDecimal value, int digits, int scale, boolean signed, byte[] out, int off) {
    BigInteger unscaled = value.setScale(scale, RoundingMode.DOWN).unscaledValue();
    boolean negative = signed && unscaled.signum() < 0;
    String s = unscaled.abs().toString();
    int nibbles = length(digits) * 2 - 1;
    if (s.length() > digits) {
      s = s.substring(s.length() - digits);
    }
    s = "0".repeat(nibbles - s.length()) + s;
    int sign = signed ? (negative ? 0x0D : 0x0C) : 0x0F;
    int len = length(digits);
    for (int i = 0; i < len; i++) {
      int hi = s.charAt(2 * i) - '0';
      int lo = i < len - 1 ? s.charAt(2 * i + 1) - '0' : sign;
      out[off + i] = (byte) ((hi << 4) | lo);
    }
  }

  static String hex(byte[] b, int off, int len) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < len; i++) {
      sb.append(String.format("%02X", b[off + i] & 0xFF));
    }
    return sb.toString();
  }
}
