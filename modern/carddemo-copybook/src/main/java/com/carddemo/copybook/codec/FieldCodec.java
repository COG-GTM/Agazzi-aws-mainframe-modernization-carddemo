package com.carddemo.copybook.codec;

import com.carddemo.copybook.ZonedDecimal;
import com.carddemo.copybook.layout.LayoutNode;
import com.carddemo.copybook.layout.Picture;
import com.carddemo.copybook.layout.Usage;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

/** Encodes and decodes one elementary item. */
public final class FieldCodec {

  private FieldCodec() {}

  /**
   * Decodes the item at absolute {@code off} into {@code target} (String, BigDecimal, int, long).
   */
  public static Object decode(LayoutNode n, byte[] b, int off, CodecOptions o, Class<?> target) {
    Picture p = n.picture();
    if (!p.numeric() || (target == String.class && n.usage() == Usage.DISPLAY)) {
      return new String(b, off, n.length(), o.charset());
    }
    BigDecimal d;
    try {
      d =
          switch (n.usage()) {
            case DISPLAY ->
                ZonedDecimal.decode(
                    new String(b, off, n.length(), o.charset()), p.scale(), p.signed());
            case PACKED -> PackedDecimal.decode(b, off, n.length(), p.scale());
            case BINARY, NATIVE_BINARY ->
                new BigDecimal(BinaryInteger.decode(b, off, n.length(), p.signed()), p.scale());
          };
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(n.name() + " @" + off + ": " + e.getMessage(), e);
    }
    return convert(d, target, n);
  }

  /** Encodes {@code value} (null = spaces for alphanumerics, zero for numerics) at {@code off}. */
  public static void encode(LayoutNode n, Object value, byte[] out, int off, CodecOptions o) {
    Picture p = n.picture();
    if (!p.numeric() || (value instanceof String && n.usage() == Usage.DISPLAY)) {
      writeText(value == null ? "" : value.toString(), out, off, n.length(), o);
      return;
    }
    BigDecimal d = value == null ? BigDecimal.ZERO : toBigDecimal(value);
    switch (n.usage()) {
      case DISPLAY ->
          writeText(
              ZonedDecimal.encode(d, n.length(), p.scale(), p.signed(), o.signStyle()),
              out,
              off,
              n.length(),
              o);
      case PACKED -> PackedDecimal.encode(d, p.digits(), p.scale(), p.signed(), out, off);
      case BINARY ->
          BinaryInteger.encode(unscaled(d, p), n.length(), p.digits(), p.signed(), out, off);
      case NATIVE_BINARY ->
          BinaryInteger.encode(unscaled(d, p), n.length(), 0, p.signed(), out, off);
      default -> throw new IllegalStateException(n.usage().name());
    }
  }

  private static BigInteger unscaled(BigDecimal d, Picture p) {
    return d.setScale(p.scale(), java.math.RoundingMode.DOWN).unscaledValue();
  }

  private static void writeText(String s, byte[] out, int off, int len, CodecOptions o) {
    byte[] bytes = s.getBytes(o.charset());
    int n = Math.min(len, bytes.length);
    System.arraycopy(bytes, 0, out, off, n);
    Arrays.fill(out, off + n, off + len, o.space());
  }

  static Object convert(BigDecimal d, Class<?> target, LayoutNode n) {
    if (target == BigDecimal.class || target == Object.class) {
      return d;
    }
    if (target == long.class || target == Long.class) {
      return d.longValueExact();
    }
    if (target == int.class || target == Integer.class) {
      return d.intValueExact();
    }
    if (target == short.class || target == Short.class) {
      return d.shortValueExact();
    }
    if (target == String.class) {
      return d.toPlainString();
    }
    throw new IllegalStateException("Unsupported Java type " + target + " for " + n.name());
  }

  static BigDecimal toBigDecimal(Object v) {
    if (v instanceof BigDecimal b) {
      return b;
    }
    if (v instanceof Number num) {
      return BigDecimal.valueOf(num.longValue());
    }
    return new BigDecimal(v.toString().trim());
  }
}
