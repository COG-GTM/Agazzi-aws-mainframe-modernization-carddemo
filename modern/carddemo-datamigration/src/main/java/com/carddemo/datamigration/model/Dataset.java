package com.carddemo.datamigration.model;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.contracts.record.DisclosureGroupRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionCategoryRecord;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.contracts.record.TransactionTypeRecord;
import com.carddemo.contracts.record.UserSecurityRecord;
import com.carddemo.copybook.ZonedRecordCodec;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Legacy dataset to V1 table mapping (plan §3.2 table "VSAM -> tabelle"). Order = load order
 * (parents before children); unload uses the same order.
 */
public enum Dataset {
  TRANTYPE(
      "transaction_type",
      TransactionTypeRecord.class,
      Organization.KSDS,
      "trantype.txt",
      "AWS.M2.CARDDEMO.TRANTYPE.PS",
      2,
      List.of(),
      List.of()),
  TRANCATG(
      "transaction_category",
      TransactionCategoryRecord.class,
      Organization.KSDS,
      "trancatg.txt",
      "AWS.M2.CARDDEMO.TRANCATG.PS",
      6,
      List.of(),
      List.of()),
  DISCGRP(
      "disclosure_group",
      DisclosureGroupRecord.class,
      Organization.KSDS,
      "discgrp.txt",
      "AWS.M2.CARDDEMO.DISCGRP.PS",
      16,
      List.of("DIS-INT-RATE"),
      List.of()),
  CUSTDATA(
      "customer",
      CustomerRecord.class,
      Organization.KSDS,
      "custdata.txt",
      "AWS.M2.CARDDEMO.CUSTDATA.PS",
      9,
      List.of(),
      List.of(DateRule.date("CUST-DOB-YYYY-MM-DD"))),
  ACCTDATA(
      "account",
      AccountRecord.class,
      Organization.KSDS,
      "acctdata.txt",
      "AWS.M2.CARDDEMO.ACCTDATA.PS",
      11,
      List.of("ACCT-CURR-BAL", "ACCT-CREDIT-LIMIT", "ACCT-CASH-CREDIT-LIMIT"),
      List.of(
          DateRule.date("ACCT-OPEN-DATE"),
          DateRule.date("ACCT-EXPIRAION-DATE"),
          DateRule.date("ACCT-REISSUE-DATE"))),
  CARDDATA(
      "card",
      CardRecord.class,
      Organization.KSDS,
      "carddata.txt",
      "AWS.M2.CARDDEMO.CARDDATA.PS",
      16,
      List.of(),
      List.of(DateRule.date("CARD-EXPIRAION-DATE"))),
  CARDXREF(
      "card_xref",
      CardXrefRecord.class,
      Organization.KSDS,
      "cardxref.txt",
      "AWS.M2.CARDDEMO.CARDXREF.PS",
      16,
      List.of(),
      List.of()),
  TCATBALF(
      "tran_cat_balance",
      TranCatBalanceRecord.class,
      Organization.KSDS,
      "tcatbal.txt",
      "AWS.M2.CARDDEMO.TCATBALF.PS",
      17,
      List.of("TRAN-CAT-BAL"),
      List.of()),
  TRANSACT(
      "transaction",
      TransactionRecord.class,
      Organization.KSDS,
      "transact.txt",
      "AWS.M2.CARDDEMO.TRANSACT.PS",
      16,
      List.of("TRAN-AMT"),
      List.of(DateRule.timestamp("TRAN-ORIG-TS", false), DateRule.timestamp("TRAN-PROC-TS", true))),
  DALYTRAN(
      "daily_transaction",
      DailyTransactionRecord.class,
      Organization.PS,
      "dailytran.txt",
      "AWS.M2.CARDDEMO.DALYTRAN.PS",
      0,
      List.of("DALYTRAN-AMT"),
      List.of(
          DateRule.timestamp("DALYTRAN-ORIG-TS", false),
          DateRule.timestamp("DALYTRAN-PROC-TS", true))),
  USRSEC(
      "app_user",
      UserSecurityRecord.class,
      Organization.KSDS,
      "usrsec.txt",
      "AWS.M2.CARDDEMO.USRSEC.PS",
      8,
      List.of(),
      List.of()),
  USRSEC_ESDS(
      "usrsec_esds",
      UserSecurityRecord.class,
      Organization.ESDS,
      "usrsec.txt",
      "AWS.M2.CARDDEMO.USRSEC.PS",
      0,
      List.of(),
      List.of()),
  USRSEC_RRDS(
      "usrsec_rrds",
      UserSecurityRecord.class,
      Organization.RRDS,
      "usrsec.txt",
      "AWS.M2.CARDDEMO.USRSEC.PS",
      0,
      List.of(),
      List.of());

  private final String table;
  private final RecordMapper<? extends Record> mapper;
  private final RecordCodec<? extends Record> asciiCodec;
  private final RecordCodec<? extends Record> ebcdicCodec;
  private final Organization organization;
  private final String asciiFile;
  private final String ebcdicFile;
  private final int keyLength;
  private final List<String> checksumFields;
  private final List<DateRule> dateRules;

  <T extends Record> Dataset(
      String table,
      Class<T> type,
      Organization organization,
      String asciiFile,
      String ebcdicFile,
      int keyLength,
      List<String> checksumFields,
      List<DateRule> dateRules) {
    this.table = table;
    this.mapper = new RecordMapper<>(type);
    this.asciiCodec = ZonedRecordCodec.of(type);
    this.ebcdicCodec = ZonedRecordCodec.ebcdic(type);
    this.organization = organization;
    this.asciiFile = asciiFile;
    this.ebcdicFile = ebcdicFile;
    this.keyLength = keyLength;
    this.checksumFields = checksumFields;
    this.dateRules = dateRules;
  }

  public String table() {
    return table;
  }

  @SuppressWarnings("unchecked")
  public <T extends Record> RecordMapper<T> mapper() {
    return (RecordMapper<T>) mapper;
  }

  @SuppressWarnings("unchecked")
  public <T extends Record> RecordCodec<T> codec(boolean ebcdic) {
    return (RecordCodec<T>) (ebcdic ? ebcdicCodec : asciiCodec);
  }

  public int recordLength() {
    return asciiCodec.recordLength();
  }

  public Organization organization() {
    return organization;
  }

  /** File name of this dataset in a directory laid out like {@code app/data/<format>}. */
  public String fileName(SourceFormat format) {
    return format == SourceFormat.EBCDIC ? ebcdicFile : asciiFile;
  }

  /** VSAM key length at RKP 0 (KSDS only). */
  public int keyLength() {
    return keyLength;
  }

  /** V1 primary-key columns. */
  public List<String> primaryKey() {
    return switch (organization) {
      case PS -> List.of("seq_no");
      case ESDS -> List.of("rba");
      case RRDS -> List.of("rrn");
      case KSDS -> keyColumns();
    };
  }

  /** Data columns that make up the VSAM key (KSDS) or business id (PS). */
  public List<String> keyColumns() {
    if (organization == Organization.ESDS || organization == Organization.RRDS) {
      return List.of();
    }
    if (organization == Organization.PS) {
      return List.of(mapper.fields().get(0).column());
    }
    int covered = 0;
    List<String> cols = new java.util.ArrayList<>();
    for (FieldColumn f : mapper.fields()) {
      if (covered >= keyLength) {
        break;
      }
      cols.add(f.column());
      covered += f.length();
    }
    return List.copyOf(cols);
  }

  /** Surrogate column filled from the record position (PS/ESDS/RRDS), else {@code null}. */
  public String surrogateColumn() {
    return organization == Organization.KSDS ? null : primaryKey().get(0);
  }

  /** Surrogate value for the 1-based record ordinal. */
  public long surrogate(long ordinal) {
    return organization == Organization.ESDS ? (ordinal - 1) * recordLength() : ordinal;
  }

  public List<String> checksumFields() {
    return checksumFields;
  }

  public List<DateRule> dateRules() {
    return dateRules;
  }

  /** {@code ALL} or a comma separated list. */
  public static List<Dataset> parseList(String spec) {
    if (spec == null || spec.isBlank() || spec.equalsIgnoreCase("ALL")) {
      return List.of(values());
    }
    return Arrays.stream(spec.split(","))
        .map(s -> valueOf(s.trim().toUpperCase(Locale.ROOT)))
        .sorted()
        .toList();
  }
}
