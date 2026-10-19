package com.carddemo.batch.interest.intcalc;

import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.ACCOUNT;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.CARD_XREF;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.DISCLOSURE_GROUP;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.TRAN_CAT_BALANCE;
import static com.carddemo.batch.interest.testsupport.RecordAssert.assertRecords;
import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.interest.B2TestApplication;
import com.carddemo.batch.interest.idcams.VsamReloadJobConfig;
import com.carddemo.batch.interest.testsupport.FixtureDb;
import com.carddemo.batch.interest.testsupport.Generations;
import com.carddemo.batch.interest.testsupport.GoldenFixture;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.copybook.CoreCodecs;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B2 on PostgreSQL 16 (Flyway V1/V3, JobRepository on PG): TCATBALF reload, then INTCALC golden.
 */
@SpringBootTest(classes = B2TestApplication.class)
@Testcontainers
class IntcalcPostgresIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired JobLauncher launcher;
  @Autowired GenerationStore generations;

  @Autowired
  @Qualifier("intcalcJob")
  Job intcalc;

  @Autowired
  @Qualifier("tcatbalfJob")
  Job tcatbalf;

  @Test
  void dailyInterestCycleMatchesTheCobolGoldenOnPostgres() throws Exception {
    GoldenFixture g = GoldenFixture.load("batch/INTCALC");
    FixtureDb db = new FixtureDb(jdbc);
    db.clear();
    db.load(ACCOUNT, g.input("ACCTDATA"));
    db.load(CARD_XREF, g.input("CARDXREF"));
    db.load(DISCLOSURE_GROUP, g.input("DISCGRP"));
    JobExecution reload =
        launcher.run(
            tcatbalf,
            new JobParametersBuilder()
                .addString(
                    VsamReloadJobConfig.TCATBAL_PARAMETER,
                    GoldenFixture.modernRoot()
                        .resolve(
                            "carddemo-parity/src/test/resources/golden/batch/POSTTRAN/expected/"
                                + "TCATBALF.dat")
                        .toString())
                .toJobParameters());
    assertThat(reload.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertRecords(CoreCodecs.TRAN_CAT_BALANCE, g.input("TCATBALF"), db.unload(TRAN_CAT_BALANCE));

    JobExecution ex =
        launcher.run(
            intcalc,
            new JobParametersBuilder()
                .addString(IntcalcTasklet.PARM, "2022071800")
                .toJobParameters());

    assertThat(ex.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertRecords(CoreCodecs.ACCOUNT, g.expected("ACCTDATA", 300), db.unload(ACCOUNT));
    assertRecords(
        CoreCodecs.TRANSACTION,
        g.expected("SYSTRAN", 350),
        Generations.current(generations, IntcalcTasklet.SYSTRAN));
  }
}
