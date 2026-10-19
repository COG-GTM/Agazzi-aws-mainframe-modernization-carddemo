package com.carddemo.ext.mqinquiry;

import java.nio.charset.StandardCharsets;

/** Fixed MQ buffer layout shared by CODATE01 and COACCT01 (MQ-BUFFER / REPLY-MESSAGE X(1000)). */
public final class MqMessageLayout {

  /** MQ-BUFFER-LENGTH of every request and reply. */
  public static final int MESSAGE_LENGTH = 1000;

  private MqMessageLayout() {}

  /**
   * Maps a message onto the single-byte MQ repertoire (US-ASCII): every character that has no
   * single-byte representation becomes {@code ?}, so character length equals byte length.
   */
  public static String ascii(String value) {
    if (value == null) {
      return "";
    }
    return new String(value.getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII);
  }

  /** COBOL MOVE into PIC X(n): left-justified, space padded, truncated on the right. */
  public static String pic(String value, int length) {
    String v = ascii(value);
    if (v.length() >= length) {
      return v.substring(0, length);
    }
    return v + " ".repeat(length - v.length());
  }

  /** A full 1000-byte MQ buffer. */
  public static String buffer(String value) {
    return pic(value, MESSAGE_LENGTH);
  }

  /** COBOL MOVE of a binary count into PIC 9(n) DISPLAY (absolute value, high-order truncation). */
  public static String unsigned(long value, int digits) {
    String s = Long.toString(Math.abs(value));
    if (s.length() > digits) {
      return s.substring(s.length() - digits);
    }
    return "0".repeat(digits - s.length()) + s;
  }
}
