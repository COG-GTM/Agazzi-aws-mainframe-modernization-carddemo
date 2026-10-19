package com.carddemo.batch.interest.intcalc;

import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.platform.math.CobolMath;
import com.carddemo.platform.math.CobolMath.Mode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pure CBACT04C arithmetic and record building (1300-COMPUTE-INTEREST, 1300-B-WRITE-TX). */
public final class InterestCalculator {

  /** {@code 1200} in {@code (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}: annual % to monthly rate. */
  static final BigDecimal MONTHS_PERCENT = BigDecimal.valueOf(1200);

  static final String TRAN_TYPE_CD = "01";
  static final int TRAN_CAT_CD = 5;
  static final String TRAN_SOURCE = "System";
  static final String DESC_PREFIX = "Int. for a/c ";

  private InterestCalculator() {}

  /**
   * {@code COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}, S9(9)V99, no ROUNDED.
   */
  public static BigDecimal monthlyInterest(BigDecimal tranCatBal, BigDecimal disIntRate) {
    return CobolMath.divide(tranCatBal.multiply(disIntRate), MONTHS_PERCENT, 9, 2, Mode.TRUNCATE);
  }

  /** {@code ADD WS-MONTHLY-INT TO WS-TOTAL-INT} (S9(9)V99). */
  public static BigDecimal addToTotal(BigDecimal total, BigDecimal monthly) {
    return CobolMath.add(total, monthly, 9, 2, Mode.TRUNCATE);
  }

  /** {@code ADD WS-TOTAL-INT TO ACCT-CURR-BAL} (S9(10)V99). */
  public static BigDecimal newBalance(BigDecimal currentBalance, BigDecimal totalInterest) {
    return CobolMath.add(currentBalance, totalInterest, 10, 2, Mode.TRUNCATE);
  }

  /** {@code STRING PARM-DATE WS-TRANID-SUFFIX DELIMITED BY SIZE INTO TRAN-ID}. */
  public static String tranId(String parmDate, int suffix) {
    String date = pad(parmDate, 10);
    String seq = "%06d".formatted(suffix % 1_000_000);
    return date + seq;
  }

  /** Z-GET-DB2-FORMAT-TIMESTAMP: {@code YYYY-MM-DD-HH.MM.SS.hh0000} (hundredths + '0000'). */
  public static String db2Timestamp(LocalDateTime now) {
    return "%04d-%02d-%02d-%02d.%02d.%02d.%02d0000"
        .formatted(
            now.getYear(),
            now.getMonthValue(),
            now.getDayOfMonth(),
            now.getHour(),
            now.getMinute(),
            now.getSecond(),
            now.getNano() / 10_000_000);
  }

  /** The SYSTRAN record written by 1300-B-WRITE-TX. */
  public static TransactionRecord interestTransaction(
      String tranId, long acctId, BigDecimal monthlyInterest, String cardNum, String db2Ts) {
    return new TransactionRecord(
        tranId,
        TRAN_TYPE_CD,
        TRAN_CAT_CD,
        pad(TRAN_SOURCE, 10),
        pad(DESC_PREFIX + "%011d".formatted(acctId), 100),
        monthlyInterest,
        0,
        pad("", 50),
        pad("", 50),
        pad("", 10),
        pad(cardNum, 16),
        db2Ts,
        db2Ts,
        pad("", 20));
  }

  /** {@code PIC X(n)} move: left-justified, space padded, truncated on the right. */
  public static String pad(String value, int length) {
    String v = value == null ? "" : value;
    return v.length() >= length ? v.substring(0, length) : v + " ".repeat(length - v.length());
  }
}
