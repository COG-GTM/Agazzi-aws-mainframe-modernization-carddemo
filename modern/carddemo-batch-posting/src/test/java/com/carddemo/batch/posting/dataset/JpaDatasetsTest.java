package com.carddemo.batch.posting.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.batch.posting.golden.Golden;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.DailyTransaction;
import com.carddemo.domain.entity.TranCatBalance;
import com.carddemo.domain.entity.TranCatBalanceId;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class JpaDatasetsTest {

  final DailyTransactionRepository daily = mock(DailyTransactionRepository.class);
  final AccountRepository accounts = mock(AccountRepository.class);
  final TranCatBalanceRepository tcat = mock(TranCatBalanceRepository.class);
  final TransactionRepository trans = mock(TransactionRepository.class);

  JpaDatasets datasets(Path dalytran) {
    return new JpaDatasets(
        daily,
        mock(CardXrefRepository.class),
        accounts,
        tcat,
        trans,
        mock(CustomerRepository.class),
        mock(CardRepository.class),
        dalytran);
  }

  static AccountRecord account() {
    return CoreCodecs.ACCOUNT.decodeLine(
        LineSequentialFile.read(Golden.seed("acctdata.txt"), 300).get(0));
  }

  @Test
  void dailyTransactionsComeFromTableInSeqOrderOrFromFile() {
    String raw = LineSequentialFile.read(Golden.seed("dailytran.txt"), 350).get(0);
    when(daily.findAll(Sort.by("seqNo")))
        .thenReturn(
            List.of(DailyTransaction.fromRecord(CoreCodecs.DAILY_TRANSACTION.decodeLine(raw))));
    JpaDatasets ds = datasets(null);
    assertThat(ds.dailyTransactions()).hasSize(1);
    assertThat(ds.decodeDailyTransaction(ds.dailyTransactions().get(0), 1))
        .isEqualTo(CoreCodecs.DAILY_TRANSACTION.decodeLine(raw));
    assertThat(datasets(Golden.seed("dailytran.txt")).dailyTransactions()).hasSize(300);
    ds.flush();
  }

  @Test
  void accountReadRewriteUpdatesManagedEntity() {
    AccountRecord r = account();
    Account managed = Account.fromRecord(r);
    when(accounts.findById(r.acctId())).thenReturn(Optional.of(managed));
    KeyedDataset<AccountRecord> ds = datasets(null).accounts();
    assertThat(ds.name()).isEqualTo("ACCTDATA");
    assertThat(ds.read(Keys.account(r))).contains(r);
    AccountRecord updated =
        new AccountRecord(
            r.acctId(),
            "N",
            BigDecimal.TEN.setScale(2),
            r.acctCreditLimit(),
            r.acctCashCreditLimit(),
            r.acctOpenDate(),
            r.acctExpirationDate(),
            r.acctReissueDate(),
            BigDecimal.ONE.setScale(2),
            r.acctCurrCycDebit(),
            r.acctAddrZip(),
            r.acctGroupId(),
            r.filler());
    ds.rewrite(updated);
    verify(accounts).save(managed);
    assertThat(managed.toRecord()).isEqualTo(updated);
    assertThat(ds.keyOf(r)).isEqualTo(Keys.account(r.acctId()));
  }

  @Test
  void rewriteOfMissingRecordIsStatus23() {
    when(accounts.findById(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> datasets(null).accounts().rewrite(account()))
        .isInstanceOf(RecordNotFoundException.class)
        .hasMessageContaining("not found in ACCTDATA");
  }

  @Test
  void writeChecksDuplicatesAndClearDeletesAll() {
    TransactionRecord t =
        CoreCodecs.TRANSACTION.decodeLine(
            Golden.expected(Golden.batch("COMBTRAN"), "TRANSACT", 350).get(0));
    when(trans.existsById(Keys.transaction(t))).thenReturn(false, true);
    KeyedDataset<TransactionRecord> ds = datasets(null).transactions();
    ds.write(t);
    verify(trans).save(any());
    assertThatThrownBy(() -> ds.write(t)).isInstanceOf(DuplicateKeyException.class);
    assertThatThrownBy(() -> ds.rewrite(t)).isInstanceOf(UnsupportedOperationException.class);
    ds.clear();
    verify(trans).deleteAllInBatch();
    ds.open();
    verify(trans).count();
  }

  @Test
  void tranCatBalanceKeyAndUpdate() {
    TranCatBalanceRecord r =
        new TranCatBalanceRecord(7L, "01", 3, BigDecimal.ONE.setScale(2), " ".repeat(22));
    TranCatBalanceId id = JpaDatasets.tranCatBalanceId(Keys.tranCatBalance(r));
    assertThat(id).isEqualTo(new TranCatBalanceId(7L, "01", 3));
    TranCatBalance managed = TranCatBalance.fromRecord(r);
    when(tcat.findById(id)).thenReturn(Optional.of(managed));
    when(tcat.findAll()).thenReturn(List.of(managed));
    KeyedDataset<TranCatBalanceRecord> ds = datasets(null).tranCatBalances();
    TranCatBalanceRecord updated =
        new TranCatBalanceRecord(7L, "01", 3, BigDecimal.TEN.setScale(2), " ".repeat(22));
    ds.rewrite(updated);
    assertThat(ds.records()).containsExactly(updated);
  }

  @Test
  void allDatasetsAreExposed() {
    JpaDatasets ds = datasets(null);
    assertThat(List.of(ds.cardXref().name(), ds.customers().name(), ds.cards().name()))
        .containsExactly("CARDXREF", "CUSTDATA", "CARDDATA");
  }
}
