package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.TranCatBalance;
import com.carddemo.domain.TranCatBalanceId;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.DailyTransactionRejectRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import com.carddemo.migration.CardDemoLoader;
import com.carddemo.migration.SampleParsers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CardDemoBatchApplication.class,
        properties = {
                "loader.input-dir=../../app/data/ASCII",
                "spring.batch.job.enabled=false",
                "spring.batch.jdbc.initialize-schema=always"
        })
@Import(CardDemoLoader.class)
class PostTransactionsJobIT {

    private static final Path DATA = Path.of("..", "..", "app", "data", "ASCII");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job postTransactionsJob;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DailyTransactionRepository dailyTransactionRepository;

    @Autowired
    private DailyTransactionRejectRepository rejectRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TranCatBalanceRepository tranCatBalanceRepository;

    @BeforeAll
    static void dockerAvailable() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void postsTransactionsAndPersistsRejects() throws Exception {
        ExpectedRun expected = expectedRun();

        JobExecution execution = jobLauncher.run(
                postTransactionsJob,
                new JobParametersBuilder()
                        .addLong("run.id", System.nanoTime())
                        .toJobParameters());

        assertThat(dailyTransactionRepository.count()).isEqualTo(300);
        assertThat(transactionRepository.count()).isEqualTo(expected.postedCount());
        assertThat(rejectRepository.count()).isEqualTo(expected.rejectCount());
        assertThat(expected.postedCount() + expected.rejectCount()).isEqualTo(300);
        assertThat(expected.rejectCount()).isGreaterThan(0);
        assertThat(rejectRepository.findAll())
                .allSatisfy(reject -> assertThat(reject.getReasonCode())
                        .isIn(100, 101, 102, 103));
        assertThat(execution.getExitStatus().getExitCode()).isNotEqualTo("COMPLETED");

        ExpectedTransaction sample = expected.postedTransactions().get(0);
        Account expectedAccount = expected.accounts().get(sample.accountId());
        Account actualAccount = accountRepository.findById(sample.accountId()).orElseThrow();
        assertThat(actualAccount.getCurrentBalance())
                .isEqualByComparingTo(expectedAccount.getCurrentBalance());
        assertThat(actualAccount.getCurrentCycleCredit())
                .isEqualByComparingTo(expectedAccount.getCurrentCycleCredit());
        assertThat(actualAccount.getCurrentCycleDebit())
                .isEqualByComparingTo(expectedAccount.getCurrentCycleDebit());

        TranCatBalanceId balanceId = new TranCatBalanceId(
                sample.accountId(),
                sample.transaction().getTypeCode(),
                sample.transaction().getCategoryCode());
        TranCatBalance actualBalance = tranCatBalanceRepository.findById(balanceId).orElseThrow();
        assertThat(actualBalance.getBalance())
                .isEqualByComparingTo(expected.categoryBalances().get(balanceId));
    }

    private ExpectedRun expectedRun() throws Exception {
        List<CardXref> xrefs = SampleParsers.cardXrefs(DATA);
        List<Account> accounts = SampleParsers.accounts(DATA);
        List<DailyTransaction> transactions = SampleParsers.dailyTransactions(DATA).stream()
                .sorted(Comparator.comparing(DailyTransaction::getId))
                .toList();
        List<TranCatBalance> balances = SampleParsers.tranCatBalances(DATA);

        Map<String, CardXref> xrefByCard = new HashMap<>();
        for (CardXref xref : xrefs) {
            xrefByCard.put(xref.getCardNumber(), xref);
        }
        Map<Long, Account> accountState = new HashMap<>();
        for (Account account : accounts) {
            accountState.put(account.getAcctId(), copy(account));
        }
        Map<TranCatBalanceId, BigDecimal> categoryBalances = new HashMap<>();
        for (TranCatBalance balance : balances) {
            categoryBalances.put(balance.getId(), balance.getBalance());
        }

        int rejectCount = 0;
        List<ExpectedTransaction> posted = new java.util.ArrayList<>();
        for (DailyTransaction transaction : transactions) {
            CardXref xref = xrefByCard.get(transaction.getCardNumber());
            if (xref == null) {
                rejectCount++;
                continue;
            }
            Account account = accountState.get(xref.getAccountId());
            if (account == null) {
                rejectCount++;
                continue;
            }
            BigDecimal temporaryBalance = account.getCurrentCycleCredit()
                    .subtract(account.getCurrentCycleDebit())
                    .add(transaction.getAmount());
            if (account.getCreditLimit().compareTo(temporaryBalance) < 0
                    || account.getExpirationDate()
                    .compareTo(transaction.getOriginalTimestamp().substring(0, 10)) < 0) {
                rejectCount++;
                continue;
            }

            account.setCurrentBalance(account.getCurrentBalance().add(transaction.getAmount()));
            if (transaction.getAmount().signum() >= 0) {
                account.setCurrentCycleCredit(
                        account.getCurrentCycleCredit().add(transaction.getAmount()));
            } else {
                account.setCurrentCycleDebit(
                        account.getCurrentCycleDebit().add(transaction.getAmount()));
            }
            TranCatBalanceId balanceId = new TranCatBalanceId(
                    xref.getAccountId(),
                    transaction.getTypeCode(),
                    transaction.getCategoryCode());
            categoryBalances.merge(balanceId, transaction.getAmount(), BigDecimal::add);
            posted.add(new ExpectedTransaction(transaction, xref.getAccountId()));
        }
        return new ExpectedRun(
                accountState,
                categoryBalances,
                posted,
                rejectCount);
    }

    private static Account copy(Account source) {
        Account copy = new Account();
        copy.setAcctId(source.getAcctId());
        copy.setCurrentBalance(source.getCurrentBalance());
        copy.setCurrentCycleCredit(source.getCurrentCycleCredit());
        copy.setCurrentCycleDebit(source.getCurrentCycleDebit());
        copy.setCreditLimit(source.getCreditLimit());
        copy.setExpirationDate(source.getExpirationDate());
        return copy;
    }

    private record ExpectedRun(
            Map<Long, Account> accounts,
            Map<TranCatBalanceId, BigDecimal> categoryBalances,
            List<ExpectedTransaction> postedTransactions,
            int rejectCount) {

        private int postedCount() {
            return postedTransactions.size();
        }
    }

    private record ExpectedTransaction(DailyTransaction transaction, long accountId) {
    }
}
