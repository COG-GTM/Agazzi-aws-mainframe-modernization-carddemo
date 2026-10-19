package com.carddemo.copybook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.carddemo.copybook.codec.BinaryInteger;
import com.carddemo.copybook.codec.PackedDecimal;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Seeded property tests for COMP-3, COMP/BINARY and zoned DISPLAY: signs, limits, truncation. */
class PrimitiveCodecsPropertyTest {

  static final int RUNS = 20_000;

  static BigInteger randomUnscaled(Random r, int digits) {
    BigInteger max = BigInteger.TEN.pow(digits);
    BigInteger v = new BigInteger(max.bitLength() + 8, r).mod(max);
    return r.nextBoolean() ? v.negate() : v;
  }

  @Test
  void packedRoundTripsForEveryPrecisionAndSign() {
    Random r = new Random(0x1A);
    for (int i = 0; i < RUNS; i++) {
      int digits = 1 + r.nextInt(18);
      int scale = r.nextInt(Math.min(digits, 4) + 1);
      boolean signed = r.nextBoolean();
      BigDecimal v = new BigDecimal(randomUnscaled(r, digits), scale);
      byte[] buf = new byte[PackedDecimal.length(digits)];
      PackedDecimal.encode(v, digits, scale, signed, buf, 0);
      assertThat(PackedDecimal.valid(buf, 0, buf.length)).isTrue();
      BigDecimal back = PackedDecimal.decode(buf, 0, buf.length, scale);
      assertThat(back).isEqualTo(signed ? v : v.abs());
      int sign = buf[buf.length - 1] & 0x0F;
      assertThat(sign).isEqualTo(!signed ? 0x0F : v.signum() < 0 ? 0x0D : 0x0C);
    }
  }

  @Test
  void packedLimitsAndTruncation() {
    byte[] b = new byte[PackedDecimal.length(11)];
    assertThat(b).hasSize(6);
    PackedDecimal.encode(new BigDecimal("999999999.99"), 11, 2, true, b, 0);
    assertThat(b).containsExactly(0x99, 0x99, 0x99, 0x99, 0x99, 0x9C);
    PackedDecimal.encode(new BigDecimal("-999999999.99"), 11, 2, true, b, 0);
    assertThat(b).containsExactly(0x99, 0x99, 0x99, 0x99, 0x99, 0x9D);
    PackedDecimal.encode(new BigDecimal("1234567890.129"), 11, 2, true, b, 0);
    assertThat(PackedDecimal.decode(b, 0, 6, 2))
        .as("MOVE truncates high-order and low-order digits")
        .isEqualTo(new BigDecimal("234567890.12"));
    byte[] even = new byte[PackedDecimal.length(4)];
    assertThat(even).hasSize(3);
    PackedDecimal.encode(new BigDecimal("-1234"), 4, 0, true, even, 0);
    assertThat(even).containsExactly(0x01, 0x23, 0x4D);
    assertThat(PackedDecimal.decode(new byte[] {0x12, 0x3F}, 0, 2, 1)).isEqualTo("12.3");
    assertThat(PackedDecimal.decode(new byte[] {0x12, 0x3B}, 0, 2, 0)).isEqualTo("-123");
    assertThat(PackedDecimal.valid(new byte[] {0x40, 0x40}, 0, 2)).isFalse();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PackedDecimal.decode(new byte[] {0x1A, 0x2C}, 0, 2, 0));
  }

  @Test
  void binaryRoundTripsOn2And4And8Bytes() {
    Random r = new Random(0x1B);
    int[][] sizes = {{2, 4}, {4, 9}, {8, 18}};
    for (int i = 0; i < RUNS; i++) {
      int[] s = sizes[r.nextInt(3)];
      boolean signed = r.nextBoolean();
      BigInteger v = randomUnscaled(r, s[1]);
      byte[] buf = new byte[s[0]];
      BinaryInteger.encode(v, s[0], s[1], signed, buf, 0);
      assertThat(BinaryInteger.decode(buf, 0, s[0], signed)).isEqualTo(signed ? v : v.abs());
    }
  }

  @Test
  void binaryIsBigEndianTwosComplementWithLimits() {
    byte[] b = new byte[2];
    BinaryInteger.encode(BigInteger.valueOf(-1), 2, 4, true, b, 0);
    assertThat(b).containsExactly(0xFF, 0xFF);
    BinaryInteger.encode(BigInteger.valueOf(9999), 2, 4, true, b, 0);
    assertThat(b).containsExactly(0x27, 0x0F);
    BinaryInteger.encode(BigInteger.valueOf(12345), 2, 4, true, b, 0);
    assertThat(BinaryInteger.decode(b, 0, 2, true)).as("TRUNC(STD)").isEqualTo(2345);
    assertThat(BinaryInteger.decode(new byte[] {(byte) 0x80, 0}, 0, 2, true)).isEqualTo(-32768);
    assertThat(BinaryInteger.decode(new byte[] {(byte) 0x80, 0}, 0, 2, false)).isEqualTo(32768);
    byte[] w = new byte[4];
    BinaryInteger.encode(BigInteger.valueOf(-999_999_999), 4, 9, true, w, 0);
    assertThat(BinaryInteger.decode(w, 0, 4, true)).isEqualTo(-999_999_999);
    byte[] l = new byte[8];
    BigInteger max = BigInteger.TEN.pow(18).subtract(BigInteger.ONE);
    BinaryInteger.encode(max.negate(), 8, 18, true, l, 0);
    assertThat(BinaryInteger.decode(l, 0, 8, true)).isEqualTo(max.negate());
    BinaryInteger.encode(BigInteger.valueOf(Long.MAX_VALUE), 8, 0, true, l, 0);
    assertThat(BinaryInteger.decode(l, 0, 8, true)).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void zonedRoundTripsInBothSignStyles() {
    Random r = new Random(0x1C);
    for (int i = 0; i < RUNS; i++) {
      int digits = 1 + r.nextInt(18);
      int scale = r.nextInt(Math.min(digits, 4) + 1);
      BigDecimal v = new BigDecimal(randomUnscaled(r, digits), scale);
      for (ZonedDecimal.SignStyle style : ZonedDecimal.SignStyle.values()) {
        String text = ZonedDecimal.encode(v, digits, scale, true, style);
        assertThat(text).hasSize(digits);
        assertThat(ZonedDecimal.decode(text, scale, true)).isEqualByComparingTo(v);
      }
      String unsigned = ZonedDecimal.encode(v, digits, scale, false);
      assertThat(unsigned).containsOnlyDigits();
      assertThat(ZonedDecimal.decode(unsigned, scale, false)).isEqualByComparingTo(v.abs());
    }
  }

  @Test
  void zonedSignCharacters() {
    assertThat(ZonedDecimal.encode(new BigDecimal("-1.23"), 5, 2, true)).isEqualTo("0012L");
    assertThat(ZonedDecimal.encode(new BigDecimal("1.23"), 5, 2, true)).isEqualTo("0012C");
    assertThat(
            ZonedDecimal.encode(
                new BigDecimal("-1.23"), 5, 2, true, ZonedDecimal.SignStyle.ASCII_NATIVE))
        .isEqualTo("0012s");
    assertThat(
            ZonedDecimal.encode(
                new BigDecimal("1.23"), 5, 2, true, ZonedDecimal.SignStyle.ASCII_NATIVE))
        .isEqualTo("00123");
    assertThat(ZonedDecimal.decode("0012s", 2, true)).isEqualByComparingTo("-1.23");
    assertThat(ZonedDecimal.decode("0000p", 0, true)).isEqualByComparingTo("0");
  }
}
