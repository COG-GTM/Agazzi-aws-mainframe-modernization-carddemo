package com.carddemo.batch.interest.intcalc;

import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.ACCOUNT;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.CARD_XREF;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.DISCLOSURE_GROUP;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.TRAN_CAT_BALANCE;
import static com.carddemo.batch.interest.testsupport.RecordAssert.assertRecords;
import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.interest.B2TestApplication;
import com.carddemo.batch.interest.support.B2JobListener;
import com.carddemo.batch.interest.testsupport.FixtureDb;
import com.carddemo.batch.interest.testsupport.Generations;
import com.carddemo.batch.interest.testsupport.GoldenFixture;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.copybook.CoreCodecs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** INTCALC behaviour: restart from the failed step, empty input, abends → RC/exit code. */
@SpringBootTest(classes = B2TestApplication.class)
class IntcalcJobTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired JobLauncher launcher;
  @Autowired JobExplorer explorer;
  @Autowired GenerationStore generations;
  @Autowired B2JobListener jobListener;

  @Autowired
  @Qualifier("intcalcJob")
  Job job;

  @TempDir Path tmp;

  FixtureDb db;
  GoldenFixture groups = GoldenFixture.load("b2-interest/INTCALC-GROUPS");

  @BeforeEach
  void clean() {
    db = new FixtureDb(jdbc);
    db.clear();
  }

  private JobParameters params(String run) {
    return new JobParametersBuilder()
        .addString(IntcalcTasklet.PARM, "2022071800")
        .addString("run", run + System.nanoTime())
        .addString("SYSOUT", tmp.resolve("SYSOUT.txt").toString(), false)
        .toJobParameters();
  }

  @Test
  void restartsFromTheFailedStepAfterTheDataIsFixed() throws Exception {
    List<String> allGroups = groups.input("DISCGRP");
    db.load(ACCOUNT, groups.input("ACCTDATA"));
    db.load(CARD_XREF, groups.input("CARDXREF"));
    db.load(TRAN_CAT_BALANCE, groups.input("TCATBALF"));
    db.load(DISCLOSURE_GROUP, allGroups.stream().filter(l -> !l.startsWith("DEFAULT")).toList());
    List<String> accountsBefore = db.unload(ACCOUNT);
    int gens = Generations.latest(generations, IntcalcTasklet.SYSTRAN);
    JobParameters params = params("restart");

    JobExecution first = launcher.run(job, params);

    assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(first.getExitStatus().getExitCode()).isEqualTo("RC16");
    assertThat(jobListener.getExitCode()).isEqualTo(16);
    assertThat(db.unload(ACCOUNT)).as("abend rolls the step back").isEqualTo(accountsBefore);
    assertThat(Generations.latest(generations, IntcalcTasklet.SYSTRAN)).isEqualTo(gens);
    assertThat(Files.readAllLines(tmp.resolve("SYSOUT.txt")))
        .contains("ERROR READING DEFAULT DISCLOSURE GROUP", "ABENDING PROGRAM");

    db.load(DISCLOSURE_GROUP, allGroups.stream().filter(l -> l.startsWith("DEFAULT")).toList());
    JobExecution second = launcher.run(job, params);

    assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(second.getJobInstance().getInstanceId())
        .isEqualTo(first.getJobInstance().getInstanceId());
    assertThat(explorer.getJobExecutions(second.getJobInstance())).hasSize(2);
    assertThat(jobListener.getExitCode()).isZero();
    assertRecords(CoreCodecs.ACCOUNT, groups.expected("ACCTDATA", 300), db.unload(ACCOUNT));
    assertThat(Generations.latest(generations, IntcalcTasklet.SYSTRAN)).isEqualTo(gens + 1);
    assertRecords(
        CoreCodecs.TRANSACTION,
        groups.expected("SYSTRAN", 350),
        Generations.current(generations, IntcalcTasklet.SYSTRAN));
  }

  @Test
  void emptyTcatbalfCompletesWithAnEmptySystran() throws Exception {
    db.load(ACCOUNT, groups.input("ACCTDATA"));
    List<String> before = db.unload(ACCOUNT);

    JobExecution ex = launcher.run(job, params("empty"));

    assertThat(ex.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(ex.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
    assertThat(Generations.current(generations, IntcalcTasklet.SYSTRAN)).isEmpty();
    assertThat(db.unload(ACCOUNT)).isEqualTo(before);
    assertThat(Files.readAllLines(tmp.resolve("SYSOUT.txt")))
        .containsExactly(
            "START OF EXECUTION OF PROGRAM CBACT04C", "END OF EXECUTION OF PROGRAM CBACT04C");
  }

  @Test
  void missingCardXrefAbendsWithRc16() throws Exception {
    db.load(ACCOUNT, groups.input("ACCTDATA"));
    db.load(TRAN_CAT_BALANCE, groups.input("TCATBALF"));
    db.load(DISCLOSURE_GROUP, groups.input("DISCGRP"));

    JobExecution ex = launcher.run(job, params("noxref"));

    assertThat(ex.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(ex.getStepExecutions().iterator().next().getExitStatus().getExitCode())
        .isEqualTo("RC16");
    assertThat(jobListener.getExitCode()).isEqualTo(16);
    assertThat(Files.readAllLines(tmp.resolve("SYSOUT.txt")))
        .contains(
            "ACCOUNT NOT FOUND: 00000000001",
            "ERROR READING XREF FILE",
            "FILE STATUS IS: NNNN0023",
            "ABENDING PROGRAM");
  }
}
