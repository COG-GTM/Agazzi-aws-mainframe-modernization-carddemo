package com.carddemo.batch.posting.cbtrn02c;

import com.carddemo.batch.posting.dataset.DuplicateKeyException;
import com.carddemo.batch.posting.dataset.Keys;
import com.carddemo.batch.posting.dataset.PostingDatasets;
import com.carddemo.batch.posting.dataset.RecordNotFoundException;
import com.carddemo.batch.posting.support.CobolAbendException;
import com.carddemo.batch.posting.support.Sysout;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.platform.math.CobolMath;
import com.carddemo.platform.math.CobolMath.Mode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/** CBTRN02C: validates DALYTRAN, posts to TRANSACT/ACCTDATA/TCATBALF and writes DALYREJS. */
public final class Cbtrn02cPoster {

  public static final String PROGRAM = "CBTRN02C";

  private final BusinessClock clock;

  /**
   * FILLER of the WS TRAN-CAT-BAL-RECORD: INITIALIZE does not reset FILLER, so a created record
   * keeps the filler of the last record read INTO it (spaces before the first successful READ).
   */
  private String tranCatBalFiller = " ".repeat(22);

  public Cbtrn02cPoster(BusinessClock clock) {
    this.clock = clock;
  }

  /** Records per flush chunk (Fase 5A, finding 5B F3). */
  public static final int CHUNK_SIZE = 1000;

  /** Runs the program; {@code rejects} receives the encoded 430-byte reject records. */
  public PostingResult run(PostingDatasets ds, Consumer<String> rejects, Sysout sysout) {
    sysout.display("START OF EXECUTION OF PROGRAM " + PROGRAM);
    List<String> daily = ds.dailyTransactions();
    ds.transactions().open();
    ds.cardXref().open();
    ds.accounts().open();
    ds.tranCatBalances().open();
    int processed = 0;
    int rejected = 0;
    for (String raw : daily) {
      if (processed > 0 && processed % CHUNK_SIZE == 0) {
        ds.checkpoint();
      }
      processed++;
      DailyTransactionRecord tran = ds.decodeDailyTransaction(raw, processed);
      Validation v = validate(ds, tran);
      if (v.reason() == null) {
        post(ds, tran, v.xref(), v.account(), sysout);
      } else {
        rejected++;
        rejects.accept(RejectRecord.of(raw, v.reason()).encode());
      }
    }
    sysout.display("TRANSACTIONS PROCESSED :" + String.format(Locale.ROOT, "%09d", processed));
    sysout.display("TRANSACTIONS REJECTED  :" + String.format(Locale.ROOT, "%09d", rejected));
    int rc = rejected > 0 ? 4 : 0;
    sysout.display("END OF EXECUTION OF PROGRAM " + PROGRAM);
    return new PostingResult(processed, rejected, rc);
  }

  record Validation(RejectReason reason, CardXrefRecord xref, AccountRecord account) {}

  /** 1500-VALIDATE-TRAN: 102 is overwritten by 103 when both apply, as in the COBOL. */
  Validation validate(PostingDatasets ds, DailyTransactionRecord tran) {
    Optional<CardXrefRecord> xref = ds.cardXref().read(Keys.pic(tran.dalytranCardNum(), 16));
    if (xref.isEmpty()) {
      return new Validation(RejectReason.INVALID_CARD, null, null);
    }
    Optional<AccountRecord> acct = ds.accounts().read(Keys.account(xref.get().xrefAcctId()));
    if (acct.isEmpty()) {
      return new Validation(RejectReason.ACCOUNT_NOT_FOUND, xref.get(), null);
    }
    AccountRecord a = acct.get();
    RejectReason reason = null;
    BigDecimal tempBal =
        CobolMath.fit(
            a.acctCurrCycCredit().subtract(a.acctCurrCycDebit()).add(tran.dalytranAmt()), 9, 2);
    if (a.acctCreditLimit().compareTo(tempBal) < 0) {
      reason = RejectReason.OVERLIMIT;
    }
    String origDate = Keys.pic(tran.dalytranOrigTs(), 26).substring(0, 10);
    if (Keys.pic(a.acctExpirationDate(), 10).compareTo(origDate) < 0) {
      reason = RejectReason.EXPIRED_ACCOUNT;
    }
    return new Validation(reason, xref.get(), a);
  }

  private void post(
      PostingDatasets ds,
      DailyTransactionRecord d,
      CardXrefRecord xref,
      AccountRecord acct,
      Sysout sysout) {
    TransactionRecord tran =
        new TransactionRecord(
            d.dalytranId(),
            d.dalytranTypeCd(),
            d.dalytranCatCd(),
            d.dalytranSource(),
            d.dalytranDesc(),
            d.dalytranAmt(),
            d.dalytranMerchantId(),
            d.dalytranMerchantName(),
            d.dalytranMerchantCity(),
            d.dalytranMerchantZip(),
            d.dalytranCardNum(),
            d.dalytranOrigTs(),
            db2Timestamp(clock.now()),
            " ".repeat(20));
    updateTranCatBalance(ds, d, xref, sysout);
    updateAccount(ds, d, acct);
    try {
      ds.transactions().write(tran);
    } catch (DuplicateKeyException e) {
      throw abend(sysout, "ERROR WRITING TO TRANSACTION FILE", "22", e);
    }
  }

  private void updateTranCatBalance(
      PostingDatasets ds, DailyTransactionRecord d, CardXrefRecord xref, Sysout sysout) {
    String key = Keys.tranCatBalance(xref.xrefAcctId(), d.dalytranTypeCd(), d.dalytranCatCd());
    Optional<TranCatBalanceRecord> existing = ds.tranCatBalances().read(key);
    if (existing.isEmpty()) {
      sysout.display("TCATBAL record not found for key : " + key + ".. Creating.");
      TranCatBalanceRecord created =
          new TranCatBalanceRecord(
              xref.xrefAcctId(),
              Keys.pic(d.dalytranTypeCd(), 2),
              d.dalytranCatCd(),
              CobolMath.add(BigDecimal.ZERO.setScale(2), d.dalytranAmt(), 9, 2, Mode.TRUNCATE),
              tranCatBalFiller);
      try {
        ds.tranCatBalances().write(created);
      } catch (DuplicateKeyException e) {
        throw abend(sysout, "ERROR WRITING TRANSACTION BALANCE FILE", "22", e);
      }
    } else {
      TranCatBalanceRecord r = existing.get();
      tranCatBalFiller = r.filler();
      ds.tranCatBalances()
          .rewrite(
              new TranCatBalanceRecord(
                  r.trancatAcctId(),
                  r.trancatTypeCd(),
                  r.trancatCd(),
                  CobolMath.add(r.tranCatBal(), d.dalytranAmt(), 9, 2, Mode.TRUNCATE),
                  r.filler()));
    }
  }

  private void updateAccount(PostingDatasets ds, DailyTransactionRecord d, AccountRecord a) {
    BigDecimal amt = d.dalytranAmt();
    boolean credit = amt.signum() >= 0;
    AccountRecord updated =
        new AccountRecord(
            a.acctId(),
            a.acctActiveStatus(),
            CobolMath.add(a.acctCurrBal(), amt, 10, 2, Mode.TRUNCATE),
            a.acctCreditLimit(),
            a.acctCashCreditLimit(),
            a.acctOpenDate(),
            a.acctExpirationDate(),
            a.acctReissueDate(),
            credit
                ? CobolMath.add(a.acctCurrCycCredit(), amt, 10, 2, Mode.TRUNCATE)
                : a.acctCurrCycCredit(),
            credit
                ? a.acctCurrCycDebit()
                : CobolMath.add(a.acctCurrCycDebit(), amt, 10, 2, Mode.TRUNCATE),
            a.acctAddrZip(),
            a.acctGroupId(),
            a.filler());
    try {
      ds.accounts().rewrite(updated);
    } catch (RecordNotFoundException e) {
      // 2800-UPDATE-ACCOUNT-REC: INVALID KEY only sets reason 109, the posting goes on.
    }
  }

  private static CobolAbendException abend(
      Sysout sysout, String message, String status, RuntimeException cause) {
    sysout.display(message);
    CobolAbendException e = new CobolAbendException(message, status, cause);
    sysout.display(e.fileStatusLine());
    sysout.display("ABENDING PROGRAM");
    return e;
  }

  /** Z-GET-DB2-FORMAT-TIMESTAMP: {@code YYYY-MM-DD-HH.MM.SS.hh0000} from CURRENT-DATE. */
  public static String db2Timestamp(LocalDateTime ts) {
    return String.format(
        Locale.ROOT,
        "%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
        ts.getYear(),
        ts.getMonthValue(),
        ts.getDayOfMonth(),
        ts.getHour(),
        ts.getMinute(),
        ts.getSecond(),
        ts.getNano() / 10_000_000);
  }
}
