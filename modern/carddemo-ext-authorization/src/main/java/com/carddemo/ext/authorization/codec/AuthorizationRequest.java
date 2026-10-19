package com.carddemo.ext.authorization.codec;

import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;

/**
 * CCPAURQY pending authorization request, parsed from the CSV MQ payload as COPAUA0C does: {@code
 * UNSTRING ... DELIMITED BY ','} into PIC X fields (left-justified, space padded, truncated), PIC 9
 * fields (right-justified digits) and the amount through {@code WS-TRANSACTION-AMT-AN X(13)} and
 * {@code FUNCTION NUMVAL} into {@code S9(10)V99}.
 */
public record AuthorizationRequest(
    String authDate,
    String authTime,
    String cardNum,
    String authType,
    String cardExpiryDate,
    String messageType,
    String messageSource,
    int processingCode,
    BigDecimal transactionAmt,
    String merchantCategoryCode,
    String acqrCountryCode,
    int posEntryMode,
    String merchantId,
    String merchantName,
    String merchantCity,
    String merchantState,
    String merchantZip,
    String transactionId) {

  public static AuthorizationRequest parse(String payload) {
    String[] t = (payload == null ? "" : payload).split(",", -1);
    return new AuthorizationRequest(
        x(t, 0, 6),
        x(t, 1, 6),
        x(t, 2, 16),
        x(t, 3, 4),
        x(t, 4, 4),
        x(t, 5, 6),
        x(t, 6, 6),
        n(t, 7, 6),
        CobolMath.store(numval(x(t, 8, 13)), 10, 2, CobolMath.Mode.TRUNCATE),
        x(t, 9, 4),
        x(t, 10, 3),
        n(t, 11, 2),
        x(t, 12, 15),
        x(t, 13, 22),
        x(t, 14, 13),
        x(t, 15, 2),
        x(t, 16, 9),
        x(t, 17, 15));
  }

  /** Pads/truncates to {@code PIC X(len)}. */
  public static String pad(String v, int len) {
    String s = v == null ? "" : v;
    return s.length() >= len ? s.substring(0, len) : s + " ".repeat(len - s.length());
  }

  private static String x(String[] t, int i, int len) {
    return pad(i < t.length ? t[i] : "", len);
  }

  private static int n(String[] t, int i, int digits) {
    StringBuilder d = new StringBuilder();
    for (char c : (i < t.length ? t[i] : "").toCharArray()) {
      if (c >= '0' && c <= '9') {
        d.append(c);
      }
    }
    String s = d.length() > digits ? d.substring(d.length() - digits) : d.toString();
    return s.isEmpty() ? 0 : Integer.parseInt(s);
  }

  /** {@code FUNCTION NUMVAL}: leading/trailing spaces, optional sign (+, -, CR, DB), decimal. */
  public static BigDecimal numval(String text) {
    String s = text.strip();
    boolean negative = false;
    if (s.endsWith("CR") || s.endsWith("DB")) {
      negative = true;
      s = s.substring(0, s.length() - 2).strip();
    } else if (s.endsWith("-") || s.endsWith("+")) {
      negative = s.endsWith("-");
      s = s.substring(0, s.length() - 1).strip();
    }
    if (s.startsWith("-") || s.startsWith("+")) {
      negative = s.startsWith("-");
      s = s.substring(1).strip();
    }
    if (s.isEmpty() || !s.matches("\\d*\\.?\\d*") || ".".equals(s)) {
      return BigDecimal.ZERO;
    }
    BigDecimal v = new BigDecimal(s.startsWith(".") ? "0" + s : s.endsWith(".") ? s + "0" : s);
    return negative ? v.negate() : v;
  }
}
