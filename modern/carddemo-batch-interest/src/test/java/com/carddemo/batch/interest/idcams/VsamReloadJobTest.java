package com.carddemo.batch.interest.idcams;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.interest.B2TestApplication;
import com.carddemo.batch.interest.support.B2JobListener;
import com.carddemo.batch.interest.testsupport.FixtureDb;
import com.carddemo.batch.interest.testsupport.FixtureDb.Table;
import com.carddemo.batch.interest.testsupport.GoldenFixture;
import com.carddemo.batch.interest.testsupport.RecordAssert;
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
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** TCATBALF / DISCGRP: malformed, duplicate, missing and empty input; restart from STEP15. */
@SpringBootTest(classes = B2TestApplication.class)
class VsamReloadJobTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired JobLauncher launcher;
  @Autowired B2JobListener jobListener;

  @Autowired
  @Qualifier("tcatbalfJob")
  Job tcatbalf;

  @Autowired
  @Qualifier("discgrpJob")
  Job discgrp;

  @TempDir Path tmp;

  FixtureDb db;
  List<String> seed = GoldenFixture.load("b2-interest/TCATBALF").input("TCATBAL").subList(0, 3);

  @BeforeEach
  void clean() {
    db = new FixtureDb(jdbc);
    db.clear();
  }

  private JobParameters params(String name, Path file) {
    return new JobParametersBuilder()
        .addString(name, file.toString())
        .addLong("run", System.nanoTime())
        .toJobParameters();
  }

  private static List<String> steps(JobExecution ex) {
    return ex.getStepExecutions().stream()
        .map(s -> s.getStepName() + " " + s.getExitStatus().getExitCode())
        .toList();
  }

  @Test
  void malformedRecordEndsWithRc12AndRestartsFromStep15() throws Exception {
    db.load(Table.TRAN_CAT_BALANCE, seed.subList(0, 1));
    Path file = tmp.resolve("TCATBALF.PS");
    Files.write(file, List.of(seed.get(0), "0000000000X01000100000000000", seed.get(2)));
    JobParameters params = params(VsamReloadJobConfig.TCATBAL_PARAMETER, file);

    JobExecution first = launcher.run(tcatbalf, params);

    assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(steps(first)).containsExactly("STEP05 COMPLETED", "STEP10 COMPLETED", "STEP15 RC12");
    assertThat(first.getExitStatus().getExitCode()).isEqualTo("RC12");
    assertThat(jobListener.getExitCode()).isEqualTo(12);
    assertThat(db.count("tran_cat_balance")).as("STEP05 deleted, STEP15 rolled back").isZero();

    Files.write(file, seed);
    JobExecution second = launcher.run(tcatbalf, params);

    assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(steps(second)).containsExactly("STEP15 COMPLETED");
    RecordAssert.assertRecords(
        Table.TRAN_CAT_BALANCE.codec(),
        seed.stream().sorted().toList(),
        db.unload(Table.TRAN_CAT_BALANCE));
  }

  @Test
  void duplicateKeyEndsWithRc12() throws Exception {
    Path file = tmp.resolve("dup.ps");
    Files.write(file, List.of(seed.get(0), seed.get(0)));
    JobExecution ex = launcher.run(tcatbalf, params(VsamReloadJobConfig.TCATBAL_PARAMETER, file));
    assertThat(ex.getExitStatus().getExitCode()).isEqualTo("RC12");
    assertThat(db.count("tran_cat_balance")).isZero();
  }

  @Test
  void overlongRecordIsMalformed() throws Exception {
    Path file = tmp.resolve("long.ps");
    Files.write(file, List.of(seed.get(0) + "X"));
    JobExecution ex = launcher.run(tcatbalf, params(VsamReloadJobConfig.TCATBAL_PARAMETER, file));
    assertThat(ex.getExitStatus().getExitCode()).isEqualTo("RC12");
  }

  @Test
  void missingInputEndsWithRc12() throws Exception {
    JobExecution ex =
        launcher.run(
            discgrp, params(VsamReloadJobConfig.DISCGRP_PARAMETER, tmp.resolve("missing.ps")));
    assertThat(ex.getStatus()).isEqualTo(BatchStatus.FAILED);
    StepExecution step15 =
        ex.getStepExecutions().stream()
            .filter(s -> s.getStepName().equals("STEP15"))
            .findFirst()
            .orElseThrow();
    assertThat(step15.getExitStatus().getExitCode()).isEqualTo("RC12");
  }

  @Test
  void emptyInputLeavesAnEmptyCluster() throws Exception {
    db.load(Table.TRAN_CAT_BALANCE, seed);
    Path file = tmp.resolve("empty.ps");
    Files.write(file, List.of("", "\r"));
    JobExecution ex = launcher.run(tcatbalf, params(VsamReloadJobConfig.TCATBAL_PARAMETER, file));
    assertThat(ex.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(jobListener.getExitCode()).isZero();
    assertThat(db.count("tran_cat_balance")).isZero();
  }

  @Test
  void discgrpReloadsTheSeed() throws Exception {
    GoldenFixture g = GoldenFixture.load("b2-interest/DISCGRP");
    JobExecution ex =
        launcher.run(
            discgrp, params(VsamReloadJobConfig.DISCGRP_PARAMETER, g.inputPath("DISCGRP")));
    assertThat(ex.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(db.unload(Table.DISCLOSURE_GROUP)).hasSize(g.input("DISCGRP").size());
  }
}
