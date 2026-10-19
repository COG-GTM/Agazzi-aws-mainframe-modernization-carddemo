package com.carddemo.copybook.codec;

import java.math.BigInteger;

/** {@code COMP}/{@code BINARY}/{@code COMP-5}: big-endian two's complement in 2, 4 or 8 bytes. */
public final class BinaryInteger {

  private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

  private BinaryInteger() {}

  /** Decodes {@code len} big-endian bytes; unsigned fields are read as unsigned. */
  public static BigInteger decode(byte[] b, int off, int len, boolean signed) {
    long v = 0;
    for (int i = 0; i < len; i++) {
      v = (v << 8) | (b[off + i] & 0xFF);
    }
    if (signed) {
      int shift = 64 - 8 * len;
      return BigInteger.valueOf((v << shift) >> shift);
    }
    BigInteger u = BigInteger.valueOf(v);
    return u.signum() < 0 ? u.add(TWO_64) : u;
  }

  /**
   * Encodes {@code value} big-endian into {@code len} bytes. With {@code truncateDigits > 0} the
   * value is first truncated to that many decimal digits (COBOL {@code TRUNC(STD)} for {@code
   * COMP}); unsigned fields store the absolute value.
   */
  public static void encode(
      BigInteger value, int len, int truncateDigits, boolean signed, byte[] out, int off) {
    BigInteger v = signed ? value : value.abs();
    if (truncateDigits > 0) {
      BigInteger mod = BigInteger.TEN.pow(truncateDigits);
      BigInteger r = v.abs().mod(mod);
      v = v.signum() < 0 ? r.negate() : r;
    }
    long bits = v.longValue();
    for (int i = len - 1; i >= 0; i--) {
      out[off + i] = (byte) (bits & 0xFF);
      bits >>= 8;
    }
  }
}
