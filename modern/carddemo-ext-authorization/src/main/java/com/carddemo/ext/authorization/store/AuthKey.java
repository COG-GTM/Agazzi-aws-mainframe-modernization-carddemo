package com.carddemo.ext.authorization.store;

import com.carddemo.ext.authorization.codec.PackedDecimal;
import java.math.BigDecimal;
import java.util.HexFormat;

/**
 * PAUT9CTS: {@code PA-AUTH-DATE-9C S9(5) COMP-3} + {@code PA-AUTH-TIME-9C S9(9) COMP-3} (8 bytes).
 * Rendered as 16 hex chars when carried in the conversation state (commarea X(8)).
 */
public record AuthKey(int authDate9c, long authTime9c) {

  public byte[] toBytes() {
    byte[] b = new byte[8];
    PackedDecimal.encode(BigDecimal.valueOf(authDate9c), b, 0, 5, 0);
    PackedDecimal.encode(BigDecimal.valueOf(authTime9c), b, 3, 9, 0);
    return b;
  }

  public String toHex() {
    return HexFormat.of().formatHex(toBytes());
  }

  public static AuthKey fromBytes(byte[] b, int offset) {
    return new AuthKey(
        PackedDecimal.decode(b, offset, 5, 0).intValueExact(),
        PackedDecimal.decode(b, offset + 3, 9, 0).longValueExact());
  }

  public static AuthKey fromHex(String hex) {
    return fromBytes(HexFormat.of().parseHex(hex), 0);
  }
}
