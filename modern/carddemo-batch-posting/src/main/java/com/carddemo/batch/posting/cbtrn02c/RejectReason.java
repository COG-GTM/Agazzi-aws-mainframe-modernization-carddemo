package com.carddemo.batch.posting.cbtrn02c;

/** WS-VALIDATION-FAIL-REASON / -DESC values written to DALYREJS by CBTRN02C. */
public enum RejectReason {
  INVALID_CARD(100, "INVALID CARD NUMBER FOUND"),
  ACCOUNT_NOT_FOUND(101, "ACCOUNT RECORD NOT FOUND"),
  OVERLIMIT(102, "OVERLIMIT TRANSACTION"),
  EXPIRED_ACCOUNT(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");

  private final int code;
  private final String description;

  RejectReason(int code, String description) {
    this.code = code;
    this.description = description;
  }

  public int code() {
    return code;
  }

  public String description() {
    return description;
  }
}
