package com.carddemo.batch.posting.dataset;

import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionRecord;
import java.util.List;

/** The VSAM clusters and DALYTRAN input used by the B1 jobs, by JCL dataset role. */
public interface PostingDatasets {

  /** DALYTRAN (DD DALYTRAN, sequential) as raw 350-byte records. */
  List<String> dailyTransactions();

  /** Decodes a DALYTRAN record. */
  DailyTransactionRecord decodeDailyTransaction(String raw, int recordNo);

  KeyedDataset<CardXrefRecord> cardXref();

  KeyedDataset<AccountRecord> accounts();

  KeyedDataset<TranCatBalanceRecord> tranCatBalances();

  KeyedDataset<TransactionRecord> transactions();

  KeyedDataset<CustomerRecord> customers();

  KeyedDataset<CardRecord> cards();

  /** Makes the updates durable (end of a successful step). */
  void flush();

  /**
   * End of a chunk of input records: pushes pending updates to the store and drops cached state, so
   * the cost per record stays constant (Fase 5A, finding 5B F3). Commit stays at {@link #flush()}.
   */
  default void checkpoint() {}
}
