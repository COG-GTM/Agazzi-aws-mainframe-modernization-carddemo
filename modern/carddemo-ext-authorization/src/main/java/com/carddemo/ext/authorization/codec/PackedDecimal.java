package com.carddemo.ext.authorization.codec;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * COMP-3 (packed decimal) encode/decode with the PIC scale; sign nibble C/F positive, D negative.
 */
public final class PackedDecimal {

  private PackedDecimal() {}

  /** Byte length of a {@code S9(digits) COMP-3} field. */
  public static int length(int digits) {
    return digits / 2 + 1;
  }

  public static BigDecimal decode(byte[] buf, int offset, int digits, int scale) {
    int len = length(digits);
    StringBuilder sb = new StringBuilder(len * 2);
    for (int i = 0; i < len; i++) {
      int b = buf[offset + i] & 0xFF;
      sb.append((char) ('0' + (b >> 4)));
      if (i < len - 1) {
        sb.append((char) ('0' + (b & 0x0F)));
      }
    }
    int sign = buf[offset + len - 1] & 0x0F;
    for (int i = 0; i < sb.length(); i++) {
      if (sb.charAt(i) > '9') {
        sb.setCharAt(i, '0');
      }
    }
    BigInteger unscaled = new BigInteger(sb.toString());
    if (sign == 0x0D || sign == 0x0B) {
      unscaled = unscaled.negate();
    }
    return new BigDecimal(unscaled, scale);
  }

  /** Validity test equivalent to COBOL {@code IS NUMERIC} on a packed field. */
  public static boolean isNumeric(byte[] buf, int offset, int digits) {
    int len = length(digits);
    for (int i = 0; i < len; i++) {
      int b = buf[offset + i] & 0xFF;
      if ((b >> 4) > 9) {
        return false;
      }
      if (i < len - 1 && (b & 0x0F) > 9) {
        return false;
      }
    }
    int sign = buf[offset + len - 1] & 0x0F;
    return sign >= 0x0A;
  }

  public static void encode(BigDecimal value, byte[] buf, int offset, int digits, int scale) {
    int len = length(digits);
    BigInteger unscaled = value.movePointRight(scale).toBigInteger();
    boolean negative = unscaled.signum() < 0;
    String d = unscaled.abs().toString();
    int nibbles = len * 2 - 1;
    if (d.length() > nibbles) {
      d = d.substring(d.length() - nibbles);
    }
    StringBuilder sb = new StringBuilder();
    sb.append("0".repeat(nibbles - d.length())).append(d);
    for (int i = 0; i < len; i++) {
      int hi = sb.charAt(i * 2) - '0';
      int lo = i < len - 1 ? sb.charAt(i * 2 + 1) - '0' : (negative ? 0x0D : 0x0C);
      buf[offset + i] = (byte) ((hi << 4) | lo);
    }
  }
}
