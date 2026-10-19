package com.carddemo.contracts.port;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Encodes/decodes one fixed-length copybook record. Fase 0 ships zoned DISPLAY codecs for the core
 * records (carddemo-copybook); 1A replaces the internals (COMP, COMP-3, OCCURS, REDEFINES, EBCDIC)
 * without changing this interface.
 *
 * @param <T> record DTO type
 */
public interface RecordCodec<T> {

  /** Record length in bytes (LRECL). */
  int recordLength();

  /** Java type produced by {@link #decode(byte[])}. */
  Class<T> recordType();

  /** Decodes exactly {@link #recordLength()} bytes. */
  T decode(byte[] record);

  /** Encodes to exactly {@link #recordLength()} bytes. */
  byte[] encode(T value);

  /** Character set of the encoded bytes (ASCII sample files by default, CP037 for EBCDIC). */
  default Charset charset() {
    return StandardCharsets.US_ASCII;
  }

  /** Decodes a text line, right-padding it with spaces to the record length. */
  default T decodeLine(String line) {
    StringBuilder sb = new StringBuilder(line);
    while (sb.length() < recordLength()) {
      sb.append(' ');
    }
    return decode(sb.toString().getBytes(charset()));
  }

  /** Encodes to a text line (same bytes as {@link #encode(Object)}). */
  default String encodeLine(T value) {
    return new String(encode(value), charset());
  }
}
