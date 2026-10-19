package com.carddemo.batch.posting.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.posting.dataset.Keys;
import com.carddemo.batch.posting.dataset.LineSequentialFile;
import com.carddemo.batch.posting.golden.Golden;
import com.carddemo.batch.posting.job.Generations;
import com.carddemo.batch.posting.job.PostingJobsConfiguration;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.GenerationStore.Generation;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.TranCatBalance;
import com.carddemo.domain.entity.Transaction;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Daily cycle POSTTRAN → TRANBKP → COMBTRAN → TRANIDX (+ CBTRN01C) on PostgreSQL 16: tables equal
 * the golden masters and the DB delta of POSTTRAN is exactly the one of the COBOL run.
 */
@Testcontainers
@SpringBootTest(
    classes = DbStorageTestApp.class,
    properties = {
      "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
      "spring.batch.jdbc.initialize-schema=never",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
class PostingDbIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JobLauncher launcher;
  @Autowired GenerationStore generations;
  @Autowired AccountRepository accounts;
  @Autowired TranCatBalanceRepository tranCatBalances;
  @Autowired TransactionRepository transactions;

  @Autowired
  @Qualifier(PostingJobsConfiguration.POSTTRAN)
  Job posttran;

  @Autowired
  @Qualifier(PostingJobsConfiguration.TRANBKP)
  Job tranbkp;

  @Autowired
  @Qualifier(PostingJobsConfiguration.COMBTRAN)
  Job combtran;

  @Autowired
  @Qualifier(PostingJobsConfiguration.TRANIDX)
  Job tranidx;

  @Autowired
  @Qualifier(PostingJobsConfiguration.CBTRN01C)
  Job cbtrn01c;

  private static long runId = 1;

  private JobExecution run(Job job, JobParametersBuilder p) throws Exception {
    return launcher.run(
        job, p.addString("storage", "db").addLong("run.id", runId++).toJobParameters());
  }

  private List<String> current(String base, int lrecl) throws Exception {
    Generation g = generations.resolve(base, 0).orElseThrow();
    try (InputStream in = generations.openInput(g)) {
      return LineSequentialFile.read(in, lrecl, g.dsn());
    }
  }

  private List<String> accountTable() {
    return accounts.findAll().stream()
        .map(Account::toRecord)
        .sorted(Comparator.comparing(Keys::account))
        .map(CoreCodecs.ACCOUNT::encodeLine)
        .toList();
  }

  @Test
  void dailyPostingCycleMatchesGoldenMasters() throws Exception {
    Path golden = Golden.b1("POSTTRAN");
    List<String> accountsBefore = accountTable();

    JobExecution post = run(posttran, new JobParametersBuilder());
    assertThat(post.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(post.getExitStatus().getExitCode()).isEqualTo("RC0004");
    Golden.assertSameRejects(
        Golden.expected(golden, "DALYREJS", 430), current(Generations.DALYREJS, 430));
    List<String> accountsAfter = accountTable();
    Golden.assertSameRecords(
        "account", Golden.expected(golden, "ACCTDATA", 300), accountsAfter, CoreCodecs.ACCOUNT);
    Golden.assertSameRecords(
        "tran_cat_balance",
        Golden.expected(golden, "TCATBALF", 50),
        tranCatBalances.findAll().stream()
            .map(TranCatBalance::toRecord)
            .sorted(Comparator.comparing(Keys::tranCatBalance))
            .map(CoreCodecs.TRAN_CAT_BALANCE::encodeLine)
            .toList(),
        CoreCodecs.TRAN_CAT_BALANCE);
    List<String> posted =
        transactions.findAll().stream()
            .map(Transaction::toRecord)
            .sorted(Comparator.comparing(Keys::transaction))
            .map(CoreCodecs.TRANSACTION::encodeLine)
            .toList();
    Golden.assertSameRecords(
        "transaction", Golden.expected(golden, "TRANSACT", 350), posted, CoreCodecs.TRANSACTION);
    assertThat(changedAccounts(accountsBefore, accountsAfter))
        .isEqualTo(
            changedAccounts(
                LineSequentialFile.read(Golden.seed("acctdata.txt"), 300),
                Golden.expected(golden, "ACCTDATA", 300)));

    JobExecution bkp = run(tranbkp, new JobParametersBuilder());
    assertThat(bkp.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    Golden.assertSameRecords(
        "TRANSACT.BKUP(0)",
        Golden.expected(golden, "TRANSACT", 350),
        current(Generations.TRANSACT_BKUP, 350),
        CoreCodecs.TRANSACTION);
    assertThat(transactions.count()).isZero();

    List<String> systran = Golden.expected(Golden.batch("INTCALC"), "SYSTRAN", 350);
    JobExecution comb =
        run(
            combtran,
            new JobParametersBuilder()
                .addString(
                    "systran", Golden.batch("INTCALC").resolve("expected/SYSTRAN.dat").toString()));
    assertThat(comb.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(transactions.count()).isEqualTo(posted.size() + systran.size());

    JobExecution idx = run(tranidx, new JobParametersBuilder());
    assertThat(idx.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(idx.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

    JobExecution verify = run(cbtrn01c, new JobParametersBuilder());
    assertThat(verify.getStatus()).isEqualTo(BatchStatus.COMPLETED);
  }

  /** Account ids whose record differs between two unloads (the DB delta of the run). */
  private static Set<Long> changedAccounts(List<String> before, List<String> after) {
    Map<Long, AccountRecord> b =
        before.stream()
            .map(CoreCodecs.ACCOUNT::decodeLine)
            .collect(Collectors.toMap(AccountRecord::acctId, Function.identity()));
    Set<Long> changed = new HashSet<>();
    for (String line : after) {
      AccountRecord a = CoreCodecs.ACCOUNT.decodeLine(line);
      if (!a.equals(b.get(a.acctId()))) {
        changed.add(a.acctId());
      }
    }
    return changed;
  }
}
