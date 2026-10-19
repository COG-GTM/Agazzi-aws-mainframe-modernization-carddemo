package com.carddemo.batch.posting.cbtrn02c;

import com.carddemo.batch.posting.dataset.Keys;
import java.util.Locale;

/**
 * DALYREJS record (LRECL 430): REJECT-TRAN-DATA X(350) = the DALYTRAN record as read, followed by
 * VALIDATION-TRAILER X(80) = WS-VALIDATION-FAIL-REASON 9(4) + WS-VALIDATION-FAIL-REASON-DESC X(76).
 */
public record RejectRecord(String transactionData, int reason, String description) {

  public static final int LENGTH = 430;

  public static RejectRecord of(String rawDailyTransaction, RejectReason reason) {
    return new RejectRecord(rawDailyTransaction, reason.code(), reason.description());
  }

  public String encode() {
    return Keys.pic(transactionData, 350)
        + String.format(Locale.ROOT, "%04d", reason)
        + Keys.pic(description, 76);
  }

  public static RejectRecord decode(String line) {
    String l = Keys.pic(line, LENGTH);
    return new RejectRecord(
        l.substring(0, 350), Integer.parseInt(l.substring(350, 354)), l.substring(354));
  }
}
