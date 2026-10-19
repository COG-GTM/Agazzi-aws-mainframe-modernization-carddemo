package com.carddemo.batch.posting.dataset;

import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionRecord;
import java.util.Locale;

/** Encoded primary keys of the KSDS datasets used by B1 (same bytes as the VSAM key). */
public final class Keys {

  private Keys() {}

  /** Right-pads (or truncates) a PIC X value to {@code length}. */
  public static String pic(String value, int length) {
    String v = value == null ? "" : value;
    if (v.length() >= length) {
      return v.substring(0, length);
    }
    return v + " ".repeat(length - v.length());
  }

  public static String account(long acctId) {
    return String.format(Locale.ROOT, "%011d", acctId);
  }

  public static String account(AccountRecord r) {
    return account(r.acctId());
  }

  public static String cardXref(CardXrefRecord r) {
    return pic(r.xrefCardNum(), 16);
  }

  public static String card(CardRecord r) {
    return pic(r.cardNum(), 16);
  }

  public static String customer(int custId) {
    return String.format(Locale.ROOT, "%09d", custId);
  }

  public static String customer(CustomerRecord r) {
    return customer(r.custId());
  }

  public static String transaction(TransactionRecord r) {
    return pic(r.tranId(), 16);
  }

  /** TRAN-CAT-KEY: TRANCAT-ACCT-ID 9(11) + TRANCAT-TYPE-CD X(2) + TRANCAT-CD 9(4). */
  public static String tranCatBalance(long acctId, String typeCd, int catCd) {
    return account(acctId) + pic(typeCd, 2) + String.format(Locale.ROOT, "%04d", catCd);
  }

  public static String tranCatBalance(TranCatBalanceRecord r) {
    return tranCatBalance(r.trancatAcctId(), r.trancatTypeCd(), r.trancatCd());
  }
}
