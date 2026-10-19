package com.carddemo.perf.datagen;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.copybook.CoreCodecs;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Deterministic x{@code scale} copy of the CardDemo sample (accounts, customers, cards, xref, daily
 * and posted transactions, TCATBAL), written as:
 *
 * <ul>
 *   <li>{@code ascii/} — fixed-width records in the original layout (zoned DISPLAY + overpunch,
 *       LRECL of plan §1.3) encoded by the Fase 0 {@link CoreCodecs};
 *   <li>{@code datasets/} — the same records named after the DD/DSN used by the Spring Batch jobs
 *       in {@code storage=file} mode ({@code ACCTDATA.dat}, ...);
 *   <li>{@code csv/} + {@code load.sql} — PostgreSQL bulk load for schema V1 (psql {@code \copy});
 *   <li>{@code k6/fixtures.json} — keys sampled for the online load scenarios.
 * </ul>
 *
 * <p>Copy 0 is the sample itself, byte for byte (re-encoded at full LRECL). Copies 1..n-1 remap
 * every key with {@link KeyMapper} and perturb amounts / names with a {@link SplittableRandom}
 * seeded from {@code (seed, k)}: the same {@code seed} always yields the same bytes.
 */
public final class SyntheticDataGenerator {

  /** Posted TRANSACT ids are 1..n; daily (DALYTRAN) ids live in a disjoint high range. */
  static final long DAILY_ID_BASE = 5_000_000_000_000_000L;

  static final int FIXTURE_LIMIT = 2_000;

  private final SampleData sample;
  static final int HISTORY_SHIFT_DAYS = 35;

  private final int scale;
  private final long seed;
  private final KeyMapper keys;

  public SyntheticDataGenerator(SampleData sample, int scale, long seed) {
    this.sample = sample;
    this.scale = scale;
    this.seed = seed;
    this.keys = new KeyMapper(sample.cards().stream().map(CardRecord::cardNum).toList(), scale);
  }

  /** Record counts per output file, in generation order. */
  public record Stats(Map<String, Long> counts) {}

  public Stats generate(Path out) {
    try {
      return write(out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Stats write(Path out) throws IOException {
    Path ascii = Files.createDirectories(out.resolve("ascii"));
    Path csv = Files.createDirectories(out.resolve("csv"));
    Files.createDirectories(out.resolve("k6"));
    Map<String, Long> counts = new LinkedHashMap<>();
    List<String> fixtures = new ArrayList<>();
    List<String> fixtureTranIds = new ArrayList<>();
    try (Out acct = new Out(ascii, csv, "acctdata.txt", "account", ACCOUNT_COLS);
        Out card = new Out(ascii, csv, "carddata.txt", "card", CARD_COLS);
        Out xref = new Out(ascii, csv, "cardxref.txt", "card_xref", XREF_COLS);
        Out cust = new Out(ascii, csv, "custdata.txt", "customer", CUSTOMER_COLS);
        Out tcat = new Out(ascii, csv, "tcatbal.txt", "tran_cat_balance", TCATBAL_COLS);
        Out daly = new Out(ascii, csv, "dailytran.txt", "daily_transaction", DALYTRAN_COLS);
        Out tran = new Out(ascii, csv, "transact.txt", "transaction", TRAN_COLS)) {
      long posted = 0;
      long daily = 0;
      for (int k = 0; k < scale; k++) {
        SplittableRandom rnd = new SplittableRandom(seed * 1_000_003L + k);
        for (AccountRecord a : sample.accounts()) {
          AccountRecord r = account(k, a, rnd);
          acct.put(CoreCodecs.ACCOUNT, r, accountRow(r));
        }
        for (CustomerRecord c : sample.customers()) {
          CustomerRecord r = customer(k, c, rnd);
          cust.put(CoreCodecs.CUSTOMER, r, customerRow(r));
        }
        for (CardRecord c : sample.cards()) {
          CardRecord r = card(k, c, rnd);
          card.put(CoreCodecs.CARD, r, cardRow(r));
        }
        for (CardXrefRecord x : sample.xrefs()) {
          CardXrefRecord r =
              new CardXrefRecord(
                  keys.cardNum(k, x.xrefCardNum()),
                  keys.custId(k, x.xrefCustId()),
                  keys.acctId(k, x.xrefAcctId()),
                  x.filler());
          xref.put(CoreCodecs.CARD_XREF, r, xrefRow(r));
          if (fixtures.size() < FIXTURE_LIMIT && (k % 10 == 0)) {
            fixtures.add(
                "{\"acctId\":\"%011d\",\"cardNum\":\"%s\",\"custId\":\"%09d\"}"
                    .formatted(r.xrefAcctId(), r.xrefCardNum(), r.xrefCustId()));
          }
        }
        for (TranCatBalanceRecord t : sample.tranCatBalances()) {
          TranCatBalanceRecord r =
              new TranCatBalanceRecord(
                  keys.acctId(k, t.trancatAcctId()),
                  t.trancatTypeCd(),
                  t.trancatCd(),
                  k == 0 ? t.tranCatBal() : cents(rnd, 0, 500_000),
                  t.filler());
          tcat.put(CoreCodecs.TRAN_CAT_BALANCE, r, tcatRow(r));
        }
        for (DailyTransactionRecord d : sample.dailyTransactions()) {
          DailyTransactionRecord dr = dailyTran(k, d, rnd, ++daily);
          daly.put(CoreCodecs.DAILY_TRANSACTION, dr, dailyRow(dr));
          TransactionRecord tr = history(dr, ++posted, rnd);
          tran.put(CoreCodecs.TRANSACTION, tr, tranRow(tr));
          if (fixtureTranIds.size() < FIXTURE_LIMIT && (k % 10 == 0)) {
            fixtureTranIds.add("\"" + tr.tranId() + "\"");
          }
        }
      }
      for (Out o : List.of(acct, cust, card, xref, tcat, daly, tran)) {
        counts.put(o.file, o.count);
      }
    }
    copyReferenceFiles(ascii);
    writeDatasets(out);
    writeLoadSql(out);
    Files.writeString(
        out.resolve("k6/fixtures.json"),
        "{\"seed\":%d,\"scale\":%d,\"accounts\":[%s],\"tranIds\":[%s]}\n"
            .formatted(seed, scale, String.join(",", fixtures), String.join(",", fixtureTranIds)));
    return new Stats(counts);
  }

  private AccountRecord account(int k, AccountRecord a, SplittableRandom rnd) {
    if (k == 0) {
      return a;
    }
    BigDecimal limit = BigDecimal.valueOf(rnd.nextLong(1_000, 20_001)).setScale(2);
    return new AccountRecord(
        keys.acctId(k, a.acctId()),
        a.acctActiveStatus(),
        cents(rnd, 0, limit.movePointRight(2).longValue() / 2),
        limit,
        limit.divide(BigDecimal.TWO, 2, RoundingMode.DOWN),
        a.acctOpenDate(),
        a.acctExpirationDate(),
        a.acctReissueDate(),
        BigDecimal.ZERO.setScale(2),
        BigDecimal.ZERO.setScale(2),
        a.acctAddrZip(),
        a.acctGroupId(),
        a.filler());
  }

  private CustomerRecord customer(int k, CustomerRecord c, SplittableRandom rnd) {
    if (k == 0) {
      return c;
    }
    List<CustomerRecord> pool = sample.customers();
    return new CustomerRecord(
        keys.custId(k, c.custId()),
        pool.get(rnd.nextInt(pool.size())).custFirstName(),
        c.custMiddleName(),
        pool.get(rnd.nextInt(pool.size())).custLastName(),
        c.custAddrLine1(),
        c.custAddrLine2(),
        c.custAddrLine3(),
        c.custAddrStateCd(),
        c.custAddrCountryCd(),
        c.custAddrZip(),
        c.custPhoneNum1(),
        c.custPhoneNum2(),
        rnd.nextInt(100_000_000, 900_000_000),
        c.custGovtIssuedId(),
        c.custDobYyyyMmDd(),
        c.custEftAccountId(),
        c.custPriCardHolderInd(),
        rnd.nextInt(300, 851),
        c.filler());
  }

  private CardRecord card(int k, CardRecord c, SplittableRandom rnd) {
    if (k == 0) {
      return c;
    }
    return new CardRecord(
        keys.cardNum(k, c.cardNum()),
        keys.acctId(k, c.cardAcctId()),
        rnd.nextInt(100, 1_000),
        c.cardEmbossedName(),
        c.cardExpirationDate(),
        c.cardActiveStatus(),
        c.filler());
  }

  private DailyTransactionRecord dailyTran(
      int k, DailyTransactionRecord d, SplittableRandom rnd, long seq) {
    BigDecimal amt =
        k == 0
            ? d.dalytranAmt()
            : d.dalytranAmt()
                .multiply(BigDecimal.valueOf(rnd.nextInt(50, 151), 2))
                .setScale(2, RoundingMode.DOWN);
    return new DailyTransactionRecord(
        k == 0 ? d.dalytranId() : "%016d".formatted(DAILY_ID_BASE + seq),
        d.dalytranTypeCd(),
        d.dalytranCatCd(),
        d.dalytranSource(),
        d.dalytranDesc(),
        amt,
        d.dalytranMerchantId(),
        d.dalytranMerchantName(),
        d.dalytranMerchantCity(),
        d.dalytranMerchantZip(),
        keys.cardNum(k, d.dalytranCardNum()),
        d.dalytranOrigTs(),
        d.dalytranProcTs(),
        d.filler());
  }

  /** Posted history (TRANSACT) for CT00/CT01/TRANREPT/CREASTMT: one per daily record. */
  /**
   * Prior-cycle TRANSACT history for the same card: amount redrawn (same sign) and timestamps moved
   * {@link #HISTORY_SHIFT_DAYS} back, so POSTTRAN posting DALYTRAN does not duplicate it.
   */
  private static TransactionRecord history(
      DailyTransactionRecord d, long seq, SplittableRandom rnd) {
    String procTs = d.dalytranProcTs().isBlank() ? d.dalytranOrigTs() : d.dalytranProcTs();
    BigDecimal amt = cents(rnd, 1, 50_000);
    return new TransactionRecord(
        "%016d".formatted(seq),
        d.dalytranTypeCd(),
        d.dalytranCatCd(),
        d.dalytranSource(),
        d.dalytranDesc(),
        d.dalytranAmt().signum() < 0 ? amt.negate() : amt,
        d.dalytranMerchantId(),
        d.dalytranMerchantName(),
        d.dalytranMerchantCity(),
        d.dalytranMerchantZip(),
        d.dalytranCardNum(),
        shiftBack(d.dalytranOrigTs()),
        shiftBack(procTs),
        d.filler());
  }

  private static String shiftBack(String ts) {
    if (ts.length() < 10 || ts.isBlank()) {
      return ts;
    }
    return LocalDate.parse(ts.substring(0, 10)).minusDays(HISTORY_SHIFT_DAYS) + ts.substring(10);
  }

  private static BigDecimal cents(SplittableRandom rnd, long minCents, long maxCents) {
    return BigDecimal.valueOf(rnd.nextLong(minCents, Math.max(minCents + 1, maxCents)), 2);
  }

  private void copyReferenceFiles(Path ascii) throws IOException {
    copyReference(ascii, "discgrp.txt", CoreCodecs.DISCLOSURE_GROUP);
    copyReference(ascii, "trantype.txt", CoreCodecs.TRANSACTION_TYPE);
    copyReference(ascii, "trancatg.txt", CoreCodecs.TRANSACTION_CATEGORY);
  }

  private <T> void copyReference(Path ascii, String file, RecordCodec<T> codec) throws IOException {
    try (Writer w = Files.newBufferedWriter(ascii.resolve(file), StandardCharsets.US_ASCII)) {
      for (T r : SampleData.read(sample.dir(), file, codec)) {
        w.write(codec.encodeLine(r));
        w.write('\n');
      }
    }
  }

  /** DSN names used by the batch jobs in {@code storage=file} mode (plan §1.3). */
  static final Map<String, String> DATASETS =
      Map.ofEntries(
          Map.entry("ACCTDATA.dat", "acctdata.txt"),
          Map.entry("CARDDATA.dat", "carddata.txt"),
          Map.entry("CARDXREF.dat", "cardxref.txt"),
          Map.entry("CUSTDATA.dat", "custdata.txt"),
          Map.entry("TCATBALF.dat", "tcatbal.txt"),
          Map.entry("DALYTRAN.dat", "dailytran.txt"),
          Map.entry("TRANSACT.dat", "transact.txt"),
          Map.entry("DISCGRP.dat", "discgrp.txt"),
          Map.entry("TRANTYPE.dat", "trantype.txt"),
          Map.entry("TRANCATG.dat", "trancatg.txt"));

  private static void writeDatasets(Path out) throws IOException {
    Path ds = Files.createDirectories(out.resolve("datasets"));
    for (var e : DATASETS.entrySet()) {
      Files.copy(
          out.resolve("ascii").resolve(e.getValue()),
          ds.resolve(e.getKey()),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
  }

  static final List<String> LOAD_ORDER =
      List.of(
          "account",
          "customer",
          "card",
          "card_xref",
          "tran_cat_balance",
          "transaction",
          "daily_transaction");

  private static void writeLoadSql(Path out) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("-- Generated by carddemo-perf-datagen: run with `psql -f load.sql` from this dir.\n")
        .append("\\set ON_ERROR_STOP on\nBEGIN;\nTRUNCATE ")
        .append(String.join(", ", LOAD_ORDER))
        .append(";\n");
    Map<String, String> cols =
        Map.of(
            "account", ACCOUNT_COLS,
            "customer", CUSTOMER_COLS,
            "card", CARD_COLS,
            "card_xref", XREF_COLS,
            "tran_cat_balance", TCATBAL_COLS,
            "transaction", TRAN_COLS,
            "daily_transaction", DALYTRAN_COLS);
    for (String t : LOAD_ORDER) {
      sb.append("\\copy ")
          .append(t)
          .append(" (")
          .append(cols.get(t))
          .append(") FROM 'csv/")
          .append(t)
          .append(".csv' WITH (FORMAT csv, HEADER true)\n");
    }
    sb.append("COMMIT;\nANALYZE ").append(String.join(", ", LOAD_ORDER)).append(";\n");
    Files.writeString(out.resolve("load.sql"), sb.toString());
  }

  static final String ACCOUNT_COLS =
      "acct_id, acct_active_status, acct_curr_bal, acct_credit_limit, acct_cash_credit_limit,"
          + " acct_open_date, acct_expiration_date, acct_reissue_date, acct_curr_cyc_credit,"
          + " acct_curr_cyc_debit, acct_addr_zip, acct_group_id, filler";
  static final String CARD_COLS =
      "card_num, card_acct_id, card_cvv_cd, card_embossed_name, card_expiration_date,"
          + " card_active_status, filler";
  static final String XREF_COLS = "xref_card_num, xref_cust_id, xref_acct_id, filler";
  static final String CUSTOMER_COLS =
      "cust_id, cust_first_name, cust_middle_name, cust_last_name, cust_addr_line_1,"
          + " cust_addr_line_2, cust_addr_line_3, cust_addr_state_cd, cust_addr_country_cd,"
          + " cust_addr_zip, cust_phone_num_1, cust_phone_num_2, cust_ssn, cust_govt_issued_id,"
          + " cust_dob_yyyy_mm_dd, cust_eft_account_id, cust_pri_card_holder_ind,"
          + " cust_fico_credit_score, filler";
  static final String TCATBAL_COLS =
      "trancat_acct_id, trancat_type_cd, trancat_cd, tran_cat_bal, filler";
  static final String TRAN_COLS =
      "tran_id, tran_type_cd, tran_cat_cd, tran_source, tran_desc, tran_amt, tran_merchant_id,"
          + " tran_merchant_name, tran_merchant_city, tran_merchant_zip, tran_card_num,"
          + " tran_orig_ts, tran_proc_ts, filler";
  static final String DALYTRAN_COLS =
      "dalytran_id, dalytran_type_cd, dalytran_cat_cd, dalytran_source, dalytran_desc,"
          + " dalytran_amt, dalytran_merchant_id, dalytran_merchant_name, dalytran_merchant_city,"
          + " dalytran_merchant_zip, dalytran_card_num, dalytran_orig_ts, dalytran_proc_ts, filler";

  private static Object[] accountRow(AccountRecord r) {
    return new Object[] {
      r.acctId(),
      r.acctActiveStatus(),
      r.acctCurrBal(),
      r.acctCreditLimit(),
      r.acctCashCreditLimit(),
      r.acctOpenDate(),
      r.acctExpirationDate(),
      r.acctReissueDate(),
      r.acctCurrCycCredit(),
      r.acctCurrCycDebit(),
      r.acctAddrZip(),
      r.acctGroupId(),
      r.filler()
    };
  }

  private static Object[] cardRow(CardRecord r) {
    return new Object[] {
      r.cardNum(),
      r.cardAcctId(),
      r.cardCvvCd(),
      r.cardEmbossedName(),
      r.cardExpirationDate(),
      r.cardActiveStatus(),
      r.filler()
    };
  }

  private static Object[] xrefRow(CardXrefRecord r) {
    return new Object[] {r.xrefCardNum(), r.xrefCustId(), r.xrefAcctId(), r.filler()};
  }

  private static Object[] customerRow(CustomerRecord r) {
    return new Object[] {
      r.custId(),
      r.custFirstName(),
      r.custMiddleName(),
      r.custLastName(),
      r.custAddrLine1(),
      r.custAddrLine2(),
      r.custAddrLine3(),
      r.custAddrStateCd(),
      r.custAddrCountryCd(),
      r.custAddrZip(),
      r.custPhoneNum1(),
      r.custPhoneNum2(),
      r.custSsn(),
      r.custGovtIssuedId(),
      r.custDobYyyyMmDd(),
      r.custEftAccountId(),
      r.custPriCardHolderInd(),
      r.custFicoCreditScore(),
      r.filler()
    };
  }

  private static Object[] tcatRow(TranCatBalanceRecord r) {
    return new Object[] {
      r.trancatAcctId(), r.trancatTypeCd(), r.trancatCd(), r.tranCatBal(), r.filler()
    };
  }

  private static Object[] tranRow(TransactionRecord r) {
    return new Object[] {
      r.tranId(),
      r.tranTypeCd(),
      r.tranCatCd(),
      r.tranSource(),
      r.tranDesc(),
      r.tranAmt(),
      r.tranMerchantId(),
      r.tranMerchantName(),
      r.tranMerchantCity(),
      r.tranMerchantZip(),
      r.tranCardNum(),
      r.tranOrigTs(),
      r.tranProcTs(),
      r.filler()
    };
  }

  private static Object[] dailyRow(DailyTransactionRecord r) {
    return new Object[] {
      r.dalytranId(),
      r.dalytranTypeCd(),
      r.dalytranCatCd(),
      r.dalytranSource(),
      r.dalytranDesc(),
      r.dalytranAmt(),
      r.dalytranMerchantId(),
      r.dalytranMerchantName(),
      r.dalytranMerchantCity(),
      r.dalytranMerchantZip(),
      r.dalytranCardNum(),
      r.dalytranOrigTs(),
      r.dalytranProcTs(),
      r.filler()
    };
  }

  /** One fixed-width file + one CSV file written in lock-step. */
  private static final class Out implements AutoCloseable {
    final String file;
    final BufferedWriter ascii;
    final BufferedWriter csv;
    long count;

    Out(Path asciiDir, Path csvDir, String file, String table, String cols) throws IOException {
      this.file = file;
      this.ascii = Files.newBufferedWriter(asciiDir.resolve(file), StandardCharsets.US_ASCII);
      this.csv = Files.newBufferedWriter(csvDir.resolve(table + ".csv"), StandardCharsets.UTF_8);
      csv.write(cols.replace(" ", "") + "\n");
    }

    <T> void put(RecordCodec<T> codec, T record, Object[] row) throws IOException {
      ascii.write(codec.encodeLine(record));
      ascii.write('\n');
      PgCsv.row(csv, row);
      count++;
    }

    @Override
    public void close() throws IOException {
      try (ascii;
          csv) {
        ascii.flush();
        csv.flush();
      }
    }
  }
}
