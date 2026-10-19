package com.carddemo.batch.posting.dataset;

import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.Card;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.entity.Customer;
import com.carddemo.domain.entity.DailyTransaction;
import com.carddemo.domain.entity.TranCatBalance;
import com.carddemo.domain.entity.TranCatBalanceId;
import com.carddemo.domain.entity.Transaction;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.List;
import org.springframework.data.domain.Sort;

/**
 * Database storage on the contracts-v1 tables. DALYTRAN comes from {@code daily_transaction}
 * (ordered by {@code seq_no}, i.e. the original file order) unless a file is given.
 */
public final class JpaDatasets implements PostingDatasets {

  private final DailyTransactionRepository dailyTransactionRepository;
  private final Path dailyTransactionFile;
  private final KeyedDataset<CardXrefRecord> cardXref;
  private final KeyedDataset<AccountRecord> accounts;
  private final KeyedDataset<TranCatBalanceRecord> tranCatBalances;
  private final KeyedDataset<TransactionRecord> transactions;
  private final KeyedDataset<CustomerRecord> customers;
  private final KeyedDataset<CardRecord> cards;
  private EntityManager entityManager;

  public JpaDatasets(
      DailyTransactionRepository dailyTransactionRepository,
      CardXrefRepository cardXrefRepository,
      AccountRepository accountRepository,
      TranCatBalanceRepository tranCatBalanceRepository,
      TransactionRepository transactionRepository,
      CustomerRepository customerRepository,
      CardRepository cardRepository,
      Path dailyTransactionFile) {
    this.dailyTransactionRepository = dailyTransactionRepository;
    this.dailyTransactionFile = dailyTransactionFile;
    cardXref =
        new JpaKeyedDataset<>(
            "CARDXREF",
            cardXrefRepository,
            k -> k,
            Keys::cardXref,
            CardXref::toRecord,
            CardXref::fromRecord,
            null);
    accounts =
        new JpaKeyedDataset<>(
            "ACCTDATA",
            accountRepository,
            Long::valueOf,
            Keys::account,
            Account::toRecord,
            Account::fromRecord,
            JpaDatasets::updateAccount);
    tranCatBalances =
        new JpaKeyedDataset<>(
            "TCATBALF",
            tranCatBalanceRepository,
            JpaDatasets::tranCatBalanceId,
            Keys::tranCatBalance,
            TranCatBalance::toRecord,
            TranCatBalance::fromRecord,
            (e, r) -> {
              e.setTranCatBal(r.tranCatBal());
              e.setFiller(r.filler());
            });
    transactions =
        new JpaKeyedDataset<>(
            "TRANSACT",
            transactionRepository,
            k -> k,
            Keys::transaction,
            Transaction::toRecord,
            Transaction::fromRecord,
            null);
    customers =
        new JpaKeyedDataset<>(
            "CUSTDATA",
            customerRepository,
            Integer::valueOf,
            Keys::customer,
            Customer::toRecord,
            Customer::fromRecord,
            null);
    cards =
        new JpaKeyedDataset<>(
            "CARDDATA", cardRepository, k -> k, Keys::card, Card::toRecord, Card::fromRecord, null);
  }

  static TranCatBalanceId tranCatBalanceId(String key) {
    return new TranCatBalanceId(
        Long.valueOf(key.substring(0, 11)),
        key.substring(11, 13),
        Integer.valueOf(key.substring(13, 17)));
  }

  static void updateAccount(Account e, AccountRecord r) {
    e.setAcctActiveStatus(r.acctActiveStatus());
    e.setAcctCurrBal(r.acctCurrBal());
    e.setAcctCreditLimit(r.acctCreditLimit());
    e.setAcctCashCreditLimit(r.acctCashCreditLimit());
    e.setAcctOpenDate(r.acctOpenDate());
    e.setAcctExpirationDate(r.acctExpirationDate());
    e.setAcctReissueDate(r.acctReissueDate());
    e.setAcctCurrCycCredit(r.acctCurrCycCredit());
    e.setAcctCurrCycDebit(r.acctCurrCycDebit());
    e.setAcctAddrZip(r.acctAddrZip());
    e.setAcctGroupId(r.acctGroupId());
    e.setFiller(r.filler());
  }

  @Override
  public List<String> dailyTransactions() {
    if (dailyTransactionFile != null) {
      return LineSequentialFile.read(
          dailyTransactionFile, CoreCodecs.DAILY_TRANSACTION.recordLength());
    }
    return dailyTransactionRepository.findAll(Sort.by("seqNo")).stream()
        .map(DailyTransaction::toRecord)
        .map(CoreCodecs.DAILY_TRANSACTION::encodeLine)
        .toList();
  }

  @Override
  public DailyTransactionRecord decodeDailyTransaction(String raw, int recordNo) {
    return CodecSupport.decode(CoreCodecs.DAILY_TRANSACTION, raw, "DALYTRAN", recordNo);
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

  /** Enables {@link #checkpoint()} (flush + clear of the step persistence context). */
  public JpaDatasets withEntityManager(EntityManager em) {
    this.entityManager = em;
    return this;
  }

  @Override
  public void checkpoint() {
    if (entityManager != null && entityManager.isJoinedToTransaction()) {
      entityManager.flush();
      entityManager.clear();
    }
  }

  @Override
  public void flush() {
    // JPA changes are flushed by the step transaction commit.
  }
}
