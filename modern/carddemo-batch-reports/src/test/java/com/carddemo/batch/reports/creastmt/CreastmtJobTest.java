package com.carddemo.batch.reports.creastmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.reports.B3Fixtures;
import com.carddemo.batch.reports.ReportsTestApplication;
import com.carddemo.batch.reports.support.JobAbendException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ReportsTestApplication.class)
class CreastmtJobTest {

  @Autowired JobLauncher launcher;

  @Autowired
  @Qualifier(CreastmtJobConfig.JOB)
  Job job;

  @TempDir Path dir;

  @BeforeEach
  void inputs() {
    B3Fixtures.materialise("CREASTMT", dir);
  }

  @Test
  void runsJclStepsAndReplacesPreviousStatements() throws Exception {
    Files.writeString(dir.resolve("STMTPS.dat"), "stale\n");
    Files.writeString(dir.resolve("TRXFL.dat"), "stale\n");
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "ok"));
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(e.getStepExecutions().stream().map(StepExecution::getStepName))
        .containsExactly("DELDEF01", "STEP010", "STEP020", "STEP030", "STEP040");
    assertThat(B3Fixtures.lines(dir.resolve("STMTPS.dat"))).hasSize(1257);
    assertThat(B3Fixtures.lines(dir.resolve("STMTHTML.dat"))).hasSize(6577);
    assertThat(B3Fixtures.lines(dir.resolve("TRXFLSEQ.dat"))).hasSize(307);
  }

  @Test
  void missingAccountAbendsAndRestartResumesFromStep040() throws Exception {
    Path acct = dir.resolve("ACCTDATA.dat");
    byte[] good = Files.readAllBytes(acct);
    Files.writeString(acct, "");
    JobParameters p = B3Fixtures.params(dir, "run", "restart");
    JobExecution first = launcher.run(job, p);
    assertThat(first.getExitStatus().getExitCode()).isEqualTo("ABEND");
    assertThat(B3Fixtures.returnCodes(first)).containsEntry("STEP040", 16);

    Files.write(acct, good);
    JobExecution restart = launcher.run(job, p);
    assertThat(restart.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restart.getStepExecutions().stream().map(StepExecution::getStepName))
        .containsExactly("STEP040");
    assertThat(B3Fixtures.bytes(dir.resolve("STMTHTML.dat")))
        .isEqualTo(B3Fixtures.bytes(B3Fixtures.B3.resolve("CREASTMT/expected/STMTHTML.dat")));
  }

  @Test
  void missingCustomerAndMalformedTransactionAbend() throws Exception {
    Files.writeString(dir.resolve("CUSTDATA.dat"), "");
    assertThat(
            launcher
                .run(job, B3Fixtures.params(dir, "run", "cust"))
                .getExitStatus()
                .getExitDescription())
        .contains("ERROR READING CUSTFILE");
    B3Fixtures.materialise("CREASTMT", dir);
    List<String> tran = B3Fixtures.lines(dir.resolve("TRANSACT.dat"));
    Files.writeString(
        dir.resolve("TRANSACT.dat"),
        tran.get(0).substring(0, 132) + "ABCDEFGHIJK" + tran.get(0).substring(143) + "\n");
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "malformed"));
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("ABEND");
  }

  @Test
  void duplicateTrxflKeyAbendsRepro() throws Exception {
    List<String> tran = B3Fixtures.lines(dir.resolve("TRANSACT.dat"));
    String r = tran.get(0);
    String dup = "X" + r.substring(1);
    Files.writeString(dir.resolve("TRANSACT.dat"), r + "\n" + dup + "\n");
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "dupkey"));
    assertThat(B3Fixtures.returnCodes(e)).containsEntry("STEP020", 0);
    // different TRAN-ID: keys differ, REPRO succeeds
    Files.writeString(dir.resolve("TRXFLSEQ.dat"), r + "\n" + r + "\n");
    assertThatThrownBy(
            () ->
                com.carddemo.batch.reports.support.Ksds.load(
                    "TRXFL", B3Fixtures.lines(dir.resolve("TRXFLSEQ.dat")), 0, 32))
        .isInstanceOf(JobAbendException.class);
  }

  @Test
  void transactionTableLimits() {
    List<String> many = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      many.add("4444333322221111" + String.format("%016d", i));
    }
    assertThatThrownBy(() -> Cbstm03aService.loadTransactions(many))
        .isInstanceOf(JobAbendException.class)
        .hasMessageContaining("WS-TRAN-TBL");
    List<String> cards = new ArrayList<>();
    for (int i = 0; i < 52; i++) {
      cards.add(String.format("%016d", i) + "0000000000000001");
    }
    assertThatThrownBy(() -> Cbstm03aService.loadTransactions(cards))
        .isInstanceOf(JobAbendException.class)
        .hasMessageContaining("WS-CARD-TBL");
  }
}
