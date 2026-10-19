package com.carddemo.batch.datalifecycle.store;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.TransactionCategoryRecord;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.contracts.record.TransactionTypeRecord;
import com.carddemo.contracts.record.UserSecurityRecord;
import com.carddemo.copybook.CoreCodecs;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A VSAM KSDS of the data lifecycle jobs and the V1 table that replaces it. Columns are the
 * snake_case names of the copybook record components.
 */
public enum KsdsTable {
  ACCTDATA("AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS", "account", CoreCodecs.ACCOUNT, "acct_id"),
  CARDDATA("AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS", "card", CoreCodecs.CARD, "card_num"),
  CARDXREF(
      "AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS", "card_xref", CoreCodecs.CARD_XREF, "xref_card_num"),
  CUSTDATA("AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS", "customer", CoreCodecs.CUSTOMER, "cust_id"),
  TRANSACT("AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS", "transaction", CoreCodecs.TRANSACTION, "tran_id"),
  TRANTYPE(
      "AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS",
      "transaction_type",
      CoreCodecs.TRANSACTION_TYPE,
      "tran_type"),
  TRANCATG(
      "AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS",
      "transaction_category",
      CoreCodecs.TRANSACTION_CATEGORY,
      "tran_type_cd, tran_cat_cd"),
  USRSEC("AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS", "app_user", CoreCodecs.USER_SECURITY, "sec_usr_id");

  private final String dsn;
  private final String table;
  private final RecordCodec<? extends Record> codec;
  private final String orderBy;

  KsdsTable(String dsn, String table, RecordCodec<? extends Record> codec, String orderBy) {
    this.dsn = dsn;
    this.table = table;
    this.codec = codec;
    this.orderBy = orderBy;
  }

  public String dsn() {
    return dsn;
  }

  public String table() {
    return table;
  }

  public String orderBy() {
    return orderBy;
  }

  @SuppressWarnings("unchecked")
  public <T extends Record> RecordCodec<T> codec() {
    return (RecordCodec<T>) codec;
  }

  public Class<?> recordType() {
    return switch (this) {
      case ACCTDATA -> AccountRecord.class;
      case CARDDATA -> CardRecord.class;
      case CARDXREF -> CardXrefRecord.class;
      case CUSTDATA -> CustomerRecord.class;
      case TRANSACT -> TransactionRecord.class;
      case TRANTYPE -> TransactionTypeRecord.class;
      case TRANCATG -> TransactionCategoryRecord.class;
      case USRSEC -> UserSecurityRecord.class;
    };
  }

  public List<String> columns() {
    List<String> cols = new ArrayList<>();
    for (RecordComponent c : recordType().getRecordComponents()) {
      cols.add(column(c.getName()));
    }
    return cols;
  }

  static String column(String component) {
    return component
        .replaceAll("([A-Z])", "_$1")
        .replaceAll("([a-zA-Z])([0-9]+)", "$1_$2")
        .toLowerCase(Locale.ROOT);
  }
}
