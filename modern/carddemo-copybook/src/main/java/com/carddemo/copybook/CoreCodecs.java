package com.carddemo.copybook;

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
import java.util.LinkedHashMap;
import java.util.Map;

/** Fase 0 codecs for the 10 core VSAM records (plan §1.3) plus USRSEC, by copybook name. */
public final class CoreCodecs {

  public static final ZonedRecordCodec<AccountRecord> ACCOUNT =
      ZonedRecordCodec.of(AccountRecord.class);
  public static final ZonedRecordCodec<CardRecord> CARD = ZonedRecordCodec.of(CardRecord.class);
  public static final ZonedRecordCodec<CardXrefRecord> CARD_XREF =
      ZonedRecordCodec.of(CardXrefRecord.class);
  public static final ZonedRecordCodec<CustomerRecord> CUSTOMER =
      ZonedRecordCodec.of(CustomerRecord.class);
  public static final ZonedRecordCodec<TranCatBalanceRecord> TRAN_CAT_BALANCE =
      ZonedRecordCodec.of(TranCatBalanceRecord.class);
  public static final ZonedRecordCodec<DisclosureGroupRecord> DISCLOSURE_GROUP =
      ZonedRecordCodec.of(DisclosureGroupRecord.class);
  public static final ZonedRecordCodec<TransactionTypeRecord> TRANSACTION_TYPE =
      ZonedRecordCodec.of(TransactionTypeRecord.class);
  public static final ZonedRecordCodec<TransactionCategoryRecord> TRANSACTION_CATEGORY =
      ZonedRecordCodec.of(TransactionCategoryRecord.class);
  public static final ZonedRecordCodec<TransactionRecord> TRANSACTION =
      ZonedRecordCodec.of(TransactionRecord.class);
  public static final ZonedRecordCodec<DailyTransactionRecord> DAILY_TRANSACTION =
      ZonedRecordCodec.of(DailyTransactionRecord.class);
  public static final ZonedRecordCodec<UserSecurityRecord> USER_SECURITY =
      ZonedRecordCodec.of(UserSecurityRecord.class);

  private static final Map<String, RecordCodec<?>> BY_COPYBOOK = new LinkedHashMap<>();

  static {
    BY_COPYBOOK.put("CVACT01Y", ACCOUNT);
    BY_COPYBOOK.put("CVACT02Y", CARD);
    BY_COPYBOOK.put("CVACT03Y", CARD_XREF);
    BY_COPYBOOK.put("CVCUS01Y", CUSTOMER);
    BY_COPYBOOK.put("CVTRA01Y", TRAN_CAT_BALANCE);
    BY_COPYBOOK.put("CVTRA02Y", DISCLOSURE_GROUP);
    BY_COPYBOOK.put("CVTRA03Y", TRANSACTION_TYPE);
    BY_COPYBOOK.put("CVTRA04Y", TRANSACTION_CATEGORY);
    BY_COPYBOOK.put("CVTRA05Y", TRANSACTION);
    BY_COPYBOOK.put("CVTRA06Y", DAILY_TRANSACTION);
    BY_COPYBOOK.put("CSUSR01Y", USER_SECURITY);
  }

  private CoreCodecs() {}

  /** Codec for a copybook name (e.g. {@code CVACT01Y}), as used in golden job.yaml layouts. */
  public static RecordCodec<?> forCopybook(String copybook) {
    RecordCodec<?> c = BY_COPYBOOK.get(copybook);
    if (c == null) {
      throw new IllegalArgumentException("No Fase 0 codec for copybook " + copybook);
    }
    return c;
  }

  public static Map<String, RecordCodec<?>> all() {
    return Map.copyOf(BY_COPYBOOK);
  }
}
