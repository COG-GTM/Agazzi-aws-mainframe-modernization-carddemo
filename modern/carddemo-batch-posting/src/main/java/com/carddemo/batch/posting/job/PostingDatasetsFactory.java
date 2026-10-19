package com.carddemo.batch.posting.job;

import com.carddemo.batch.posting.dataset.FileDatasets;
import com.carddemo.batch.posting.dataset.JpaDatasets;
import com.carddemo.batch.posting.dataset.PostingDatasets;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.springframework.beans.factory.ObjectProvider;

/** Opens the datasets of a step on the storage selected by the job parameters. */
public class PostingDatasetsFactory {

  private final ObjectProvider<DailyTransactionRepository> dailyTransactions;
  private final ObjectProvider<CardXrefRepository> cardXref;
  private final ObjectProvider<AccountRepository> accounts;
  private final ObjectProvider<TranCatBalanceRepository> tranCatBalances;
  private final ObjectProvider<TransactionRepository> transactions;
  private final ObjectProvider<CustomerRepository> customers;
  private final ObjectProvider<CardRepository> cards;

  public PostingDatasetsFactory(
      ObjectProvider<DailyTransactionRepository> dailyTransactions,
      ObjectProvider<CardXrefRepository> cardXref,
      ObjectProvider<AccountRepository> accounts,
      ObjectProvider<TranCatBalanceRepository> tranCatBalances,
      ObjectProvider<TransactionRepository> transactions,
      ObjectProvider<CustomerRepository> customers,
      ObjectProvider<CardRepository> cards) {
    this.dailyTransactions = dailyTransactions;
    this.cardXref = cardXref;
    this.accounts = accounts;
    this.tranCatBalances = tranCatBalances;
    this.transactions = transactions;
    this.customers = customers;
    this.cards = cards;
  }

  public PostingDatasets open(PostingJobParameters p) {
    if (p.fileStorage()) {
      if (p.datasetDir() == null) {
        throw new IllegalArgumentException("storage=file requires the datasetDir job parameter");
      }
      return new FileDatasets(p.datasetDir(), p.dalytran());
    }
    return new JpaDatasets(
        dailyTransactions.getObject(),
        cardXref.getObject(),
        accounts.getObject(),
        tranCatBalances.getObject(),
        transactions.getObject(),
        customers.getObject(),
        cards.getObject(),
        p.dalytran());
  }
}
