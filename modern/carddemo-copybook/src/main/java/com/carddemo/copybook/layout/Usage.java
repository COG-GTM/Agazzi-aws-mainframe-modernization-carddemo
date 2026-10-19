package com.carddemo.copybook.layout;

/** Storage representation of an elementary item (COBOL {@code USAGE} clause). */
public enum Usage {
  /** {@code DISPLAY}: one character per digit, zoned sign overpunched on the last byte. */
  DISPLAY,
  /** {@code COMP}/{@code COMP-4}/{@code BINARY}: big-endian two's complement, PIC-truncated. */
  BINARY,
  /** {@code COMP-5}: big-endian two's complement, full binary range of the storage size. */
  NATIVE_BINARY,
  /** {@code COMP-3}/{@code PACKED-DECIMAL}: two digits per byte, sign in the last nibble. */
  PACKED;

  /** Parses a usage keyword; returns {@code null} when the token is not a usage keyword. */
  public static Usage fromKeyword(String keyword) {
    Usage u = fromKeywordOrNull(keyword);
    if (u == null && keyword.startsWith("COMP")) {
      throw new IllegalArgumentException(
          "Usage " + keyword + " is not supported (floating point is banned by plan §3.4)");
    }
    return u;
  }

  /** Like {@link #fromKeyword} but never throws. */
  public static Usage fromKeywordOrNull(String keyword) {
    return switch (keyword) {
      case "DISPLAY" -> DISPLAY;
      case "COMP", "COMPUTATIONAL", "COMP-4", "COMPUTATIONAL-4", "BINARY" -> BINARY;
      case "COMP-5", "COMPUTATIONAL-5" -> NATIVE_BINARY;
      case "COMP-3", "COMPUTATIONAL-3", "PACKED-DECIMAL" -> PACKED;
      default -> null;
    };
  }
}
