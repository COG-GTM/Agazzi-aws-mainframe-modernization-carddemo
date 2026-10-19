package com.carddemo.ext.authorization.codec;

import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.model.PendingAuthSummary;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Byte layouts of the IMS segments PAUTSUM0 (CIPAUSMY, 100 bytes) and PAUTDTL1 (CIPAUDTY, 200
 * bytes). Text and zoned fields use {@code charset} (IBM037 on the mainframe, ISO-8859-1 for
 * GnuCOBOL), COMP-3 and COMP are binary and charset independent.
 */
public final class PendingAuthSegments {

  public static final int SUMMARY_LENGTH = 100;
  public static final int DETAIL_LENGTH = 200;
  public static final Charset EBCDIC = Charset.forName("IBM037");
  public static final Charset ASCII = StandardCharsets.ISO_8859_1;

  private static final HexFormat HEX = HexFormat.of();

  private final Charset charset;
  private final byte[] toLatin = new byte[256];
  private final byte[] fromLatin = new byte[256];

  public PendingAuthSegments(Charset charset) {
    this.charset = charset;
    for (int v = 0; v < 256; v++) {
      char ch = new String(new byte[] {(byte) v}, charset).charAt(0);
      if (ch > 0xFF) {
        throw new IllegalArgumentException(charset + " is not a single-byte Latin-1 code page");
      }
      toLatin[v] = (byte) ch;
      fromLatin[ch] = (byte) v;
    }
  }

  public Charset charset() {
    return charset;
  }

  public byte[] encodeSummary(PendingAuthSummary s) {
    byte[] b = new byte[SUMMARY_LENGTH];
    PackedDecimal.encode(BigDecimal.valueOf(s.getAcctId()), b, 0, 11, 0);
    text(b, 6, 9, zoned(s.getCustId(), 9));
    text(b, 15, 1, s.getAuthStatus());
    text(b, 16, 10, s.getAccountStatus());
    PackedDecimal.encode(s.getCreditLimit(), b, 26, 11, 2);
    PackedDecimal.encode(s.getCashLimit(), b, 32, 11, 2);
    PackedDecimal.encode(s.getCreditBalance(), b, 38, 11, 2);
    PackedDecimal.encode(s.getCashBalance(), b, 44, 11, 2);
    binary(b, 50, s.getApprovedAuthCnt());
    binary(b, 52, s.getDeclinedAuthCnt());
    PackedDecimal.encode(s.getApprovedAuthAmt(), b, 54, 11, 2);
    PackedDecimal.encode(s.getDeclinedAuthAmt(), b, 60, 11, 2);
    raw(b, 66, PendingAuthSummary.FILLER_BYTES, s.getFiller());
    return b;
  }

  public PendingAuthSummary decodeSummary(byte[] b, int o) {
    PendingAuthSummary s = new PendingAuthSummary();
    s.setAcctId(PackedDecimal.decode(b, o, 11, 0).longValueExact());
    s.setCustId(Integer.parseInt(digits(b, o + 6, 9)));
    s.setAuthStatus(str(b, o + 15, 1));
    s.setAccountStatus(str(b, o + 16, 10));
    s.setCreditLimit(PackedDecimal.decode(b, o + 26, 11, 2));
    s.setCashLimit(PackedDecimal.decode(b, o + 32, 11, 2));
    s.setCreditBalance(PackedDecimal.decode(b, o + 38, 11, 2));
    s.setCashBalance(PackedDecimal.decode(b, o + 44, 11, 2));
    s.setApprovedAuthCnt(readShort(b, o + 50));
    s.setDeclinedAuthCnt(readShort(b, o + 52));
    s.setApprovedAuthAmt(PackedDecimal.decode(b, o + 54, 11, 2));
    s.setDeclinedAuthAmt(PackedDecimal.decode(b, o + 60, 11, 2));
    s.setFiller(rawHex(b, o + 66, PendingAuthSummary.FILLER_BYTES));
    return s;
  }

  public byte[] encodeDetail(PendingAuthDetail d) {
    byte[] b = new byte[DETAIL_LENGTH];
    PackedDecimal.encode(BigDecimal.valueOf(d.getAuthDate9c()), b, 0, 5, 0);
    PackedDecimal.encode(BigDecimal.valueOf(d.getAuthTime9c()), b, 3, 9, 0);
    text(b, 8, 6, d.getAuthOrigDate());
    text(b, 14, 6, d.getAuthOrigTime());
    text(b, 20, 16, d.getCardNum());
    text(b, 36, 4, d.getAuthType());
    text(b, 40, 4, d.getCardExpiryDate());
    text(b, 44, 6, d.getMessageType());
    text(b, 50, 6, d.getMessageSource());
    text(b, 56, 6, d.getAuthIdCode());
    text(b, 62, 2, d.getAuthRespCode());
    text(b, 64, 4, d.getAuthRespReason());
    text(b, 68, 6, zoned(d.getProcessingCode(), 6));
    PackedDecimal.encode(d.getTransactionAmt(), b, 74, 12, 2);
    PackedDecimal.encode(d.getApprovedAmt(), b, 81, 12, 2);
    text(b, 88, 4, d.getMerchantCategoryCode());
    text(b, 92, 3, d.getAcqrCountryCode());
    text(b, 95, 2, zoned(d.getPosEntryMode(), 2));
    text(b, 97, 15, d.getMerchantId());
    text(b, 112, 22, d.getMerchantName());
    text(b, 134, 13, d.getMerchantCity());
    text(b, 147, 2, d.getMerchantState());
    text(b, 149, 9, d.getMerchantZip());
    text(b, 158, 15, d.getTransactionId());
    text(b, 173, 1, d.getMatchStatus());
    text(b, 174, 1, d.getAuthFraud());
    text(b, 175, 8, d.getFraudRptDate());
    raw(b, 183, 17, d.getFiller());
    return b;
  }

  public PendingAuthDetail decodeDetail(long acctId, byte[] b, int o) {
    PendingAuthDetail d = new PendingAuthDetail();
    d.setAcctId(acctId);
    d.setAuthDate9c(PackedDecimal.decode(b, o, 5, 0).intValueExact());
    d.setAuthTime9c(PackedDecimal.decode(b, o + 3, 9, 0).longValueExact());
    d.setAuthOrigDate(str(b, o + 8, 6));
    d.setAuthOrigTime(str(b, o + 14, 6));
    d.setCardNum(str(b, o + 20, 16));
    d.setAuthType(str(b, o + 36, 4));
    d.setCardExpiryDate(str(b, o + 40, 4));
    d.setMessageType(str(b, o + 44, 6));
    d.setMessageSource(str(b, o + 50, 6));
    d.setAuthIdCode(str(b, o + 56, 6));
    d.setAuthRespCode(str(b, o + 62, 2));
    d.setAuthRespReason(str(b, o + 64, 4));
    d.setProcessingCode(Integer.parseInt(digits(b, o + 68, 6)));
    d.setTransactionAmt(PackedDecimal.decode(b, o + 74, 12, 2));
    d.setApprovedAmt(PackedDecimal.decode(b, o + 81, 12, 2));
    d.setMerchantCategoryCode(str(b, o + 88, 4));
    d.setAcqrCountryCode(str(b, o + 92, 3));
    d.setPosEntryMode((short) Integer.parseInt(digits(b, o + 95, 2)));
    d.setMerchantId(str(b, o + 97, 15));
    d.setMerchantName(str(b, o + 112, 22));
    d.setMerchantCity(str(b, o + 134, 13));
    d.setMerchantState(str(b, o + 147, 2));
    d.setMerchantZip(str(b, o + 149, 9));
    d.setTransactionId(str(b, o + 158, 15));
    d.setMatchStatus(str(b, o + 173, 1));
    d.setAuthFraud(str(b, o + 174, 1));
    d.setFraudRptDate(str(b, o + 175, 8));
    d.setFiller(rawHex(b, o + 183, 17));
    return d;
  }

  private static String zoned(Number n, int digits) {
    String s = Long.toString(Math.abs(n.longValue()));
    s = s.length() > digits ? s.substring(s.length() - digits) : s;
    return "0".repeat(digits - s.length()) + s;
  }

  private void text(byte[] b, int off, int len, String value) {
    String v = value == null ? "" : value;
    v = v.length() > len ? v.substring(0, len) : v + " ".repeat(len - v.length());
    System.arraycopy(v.getBytes(charset), 0, b, off, len);
  }

  private String str(byte[] b, int off, int len) {
    return new String(b, off, len, charset).replace('\0', ' ');
  }

  private String digits(byte[] b, int off, int len) {
    StringBuilder sb = new StringBuilder(len);
    for (char c : new String(b, off, len, charset).toCharArray()) {
      sb.append(Character.isDigit(c) ? c : '0');
    }
    return sb.toString();
  }

  /** FILLER bytes are kept as hex of their ISO-8859-1 image (charset independent). */
  private void raw(byte[] b, int off, int len, String hex) {
    if (hex == null || hex.length() != len * 2) {
      text(b, off, len, "");
      return;
    }
    byte[] latin = HEX.parseHex(hex);
    for (int i = 0; i < len; i++) {
      b[off + i] = fromLatin[latin[i] & 0xFF];
    }
  }

  private String rawHex(byte[] b, int off, int len) {
    byte[] latin = new byte[len];
    for (int i = 0; i < len; i++) {
      latin[i] = toLatin[b[off + i] & 0xFF];
    }
    return HEX.formatHex(latin);
  }

  private static void binary(byte[] b, int off, short v) {
    b[off] = (byte) (v >> 8);
    b[off + 1] = (byte) v;
  }

  private static short readShort(byte[] b, int off) {
    return (short) (((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF));
  }
}
