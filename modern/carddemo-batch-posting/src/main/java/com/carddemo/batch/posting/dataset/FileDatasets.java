package com.carddemo.batch.posting.dataset;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.copybook.CoreCodecs;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * File storage: every cluster is {@code <dir>/<DSNAME>.dat} (names as in the golden {@code
 * job.yaml}: DALYTRAN, CARDXREF, ACCTDATA, TCATBALF, TRANSACT, CUSTDATA, CARDDATA).
 */
public final class FileDatasets implements PostingDatasets {

  public static final String DALYTRAN = "DALYTRAN";
  public static final String CARDXREF = "CARDXREF";
  public static final String ACCTDATA = "ACCTDATA";
  public static final String TCATBALF = "TCATBALF";
  public static final String TRANSACT = "TRANSACT";
  public static final String CUSTDATA = "CUSTDATA";
  public static final String CARDDATA = "CARDDATA";

  private final Path dir;
  private final Path dailyTransactionFile;
  private final List<InMemoryKeyedDataset<?>> all = new ArrayList<>();
  private final InMemoryKeyedDataset<CardXrefRecord> cardXref;
  private final InMemoryKeyedDataset<AccountRecord> accounts;
  private final InMemoryKeyedDataset<TranCatBalanceRecord> tranCatBalances;
  private final InMemoryKeyedDataset<TransactionRecord> transactions;
  private final InMemoryKeyedDataset<CustomerRecord> customers;
  private final InMemoryKeyedDataset<CardRecord> cards;

  public FileDatasets(Path dir, Path dailyTransactionFile) {
    this.dir = dir;
    this.dailyTransactionFile =
        dailyTransactionFile != null ? dailyTransactionFile : file(DALYTRAN);
    cardXref = register(CARDXREF, CoreCodecs.CARD_XREF, Keys::cardXref, true);
    accounts = register(ACCTDATA, CoreCodecs.ACCOUNT, Keys::account, true);
    tranCatBalances = register(TCATBALF, CoreCodecs.TRAN_CAT_BALANCE, Keys::tranCatBalance, true);
    transactions = register(TRANSACT, CoreCodecs.TRANSACTION, Keys::transaction, false);
    customers = register(CUSTDATA, CoreCodecs.CUSTOMER, Keys::customer, true);
    cards = register(CARDDATA, CoreCodecs.CARD, Keys::card, true);
  }

  public FileDatasets(Path dir) {
    this(dir, null);
  }

  public Path file(String dsname) {
    return dir.resolve(dsname + ".dat");
  }

  private <R> InMemoryKeyedDataset<R> register(
      String name, RecordCodec<R> codec, java.util.function.Function<R, String> key, boolean must) {
    InMemoryKeyedDataset<R> ds = new InMemoryKeyedDataset<>(name, file(name), codec, key, must);
    all.add(ds);
    return ds;
  }

  @Override
  public List<String> dailyTransactions() {
    return LineSequentialFile.read(
        dailyTransactionFile, CoreCodecs.DAILY_TRANSACTION.recordLength());
  }

  @Override
  public DailyTransactionRecord decodeDailyTransaction(String raw, int recordNo) {
    return CodecSupport.decode(CoreCodecs.DAILY_TRANSACTION, raw, DALYTRAN, recordNo);
  }

  @Override
  public KeyedDataset<CardXrefRecord> cardXref() {
    return cardXref;
  }

  @Override
  public KeyedDataset<AccountRecord> accounts() {
    return accounts;
  }

  @Override
  public KeyedDataset<TranCatBalanceRecord> tranCatBalances() {
    return tranCatBalances;
  }

  @Override
  public KeyedDataset<TransactionRecord> transactions() {
    return transactions;
  }

  @Override
  public KeyedDataset<CustomerRecord> customers() {
    return customers;
  }

  @Override
  public KeyedDataset<CardRecord> cards() {
    return cards;
  }

  @Override
  public void flush() {
    all.forEach(InMemoryKeyedDataset::flush);
  }
}
