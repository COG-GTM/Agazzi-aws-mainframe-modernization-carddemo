package com.carddemo.copybook.codec;

import com.carddemo.copybook.ZonedDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Byte-level options of a codec.
 *
 * @param charset charset of alphanumeric and DISPLAY bytes: ISO-8859-1 for ASCII files (one byte
 *     per char, binary safe), CP037 for EBCDIC
 * @param signStyle how signed DISPLAY numerics are written
 */
public record CodecOptions(Charset charset, ZonedDecimal.SignStyle signStyle) {

  public static final Charset CP037 = Charset.forName("Cp037");

  /** ASCII datasets of {@code app/data/ASCII} (IBM overpunch). */
  public static final CodecOptions ASCII =
      new CodecOptions(StandardCharsets.ISO_8859_1, ZonedDecimal.SignStyle.OVERPUNCH);

  /** EBCDIC datasets of {@code app/data/EBCDIC} (CP037, zone {@code C}/{@code D}). */
  public static final CodecOptions EBCDIC =
      new CodecOptions(CP037, ZonedDecimal.SignStyle.OVERPUNCH);

  /** Files written by GnuCOBOL in the golden harness (ASCII, native sign). */
  public static final CodecOptions GNUCOBOL =
      new CodecOptions(StandardCharsets.ISO_8859_1, ZonedDecimal.SignStyle.ASCII_NATIVE);

  byte space() {
    return " ".getBytes(charset)[0];
  }
}
