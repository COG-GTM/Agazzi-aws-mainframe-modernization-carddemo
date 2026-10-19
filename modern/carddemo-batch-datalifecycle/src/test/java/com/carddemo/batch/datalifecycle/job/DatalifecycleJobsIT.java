package com.carddemo.batch.datalifecycle.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.datalifecycle.support.Goldens;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The data lifecycle chain on PostgreSQL 16 with the V1/V3/V500 Flyway migrations. */
@Tag("it")
@Testcontainers
@SpringBootTest(
    classes = JobsTestApplication.class,
    properties = {
      "spring.flyway.locations=classpath:db/migration",
      "spring.batch.job.enabled=false",
      "spring.batch.jdbc.initialize-schema=never"
    })
class DatalifecycleJobsIT {

  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", PG::getJdbcUrl);
    r.add("spring.datasource.username", PG::getUsername);
    r.add("spring.datasource.password", PG::getPassword);
  }

  @Autowired ApplicationContext ctx;
  @Autowired JobLauncher launcher;
  @Autowired JdbcTemplate jdbc;
  @TempDir Path dir;
  long run;

  JobExecution launch(String name, String... kv) throws Exception {
    Job job =
        ctx.getBeansOfType(Job.class).values().stream()
            .filter(j -> j.getName().equals(name))
            .findFirst()
            .orElseThrow();
    JobParametersBuilder b =
        new JobParametersBuilder()
            .addString("datasets", dir.resolve("datasets").toString())
            .addString("sysout", dir.resolve("SYSOUT-" + (++run) + ".txt").toString())
            .addLong("run", run);
    for (int i = 0; i < kv.length; i += 2) {
      b.addString(kv[i], kv[i + 1]);
    }
    JobExecution e = launcher.run(job, b.toJobParameters());
    assertThat(e.getStatus()).as(name).isEqualTo(BatchStatus.COMPLETED);
    return e;
  }

  @Test
  void refreshReadExportImportOnPostgres() throws Exception {
    launch("ACCTFILE", "dd.ACCTDATA", Goldens.seed("acctdata.txt").toString());
    launch("CARDFILE", "dd.CARDDATA", Goldens.seed("carddata.txt").toString());
    launch("XREFFILE", "dd.XREFDATA", Goldens.seed("cardxref.txt").toString());
    launch("CUSTFILE", "dd.CUSTDATA", Goldens.seed("custdata.txt").toString());
    launch("TRANFILE", "dd.TRANSACT", Goldens.seed("dailytran.txt").toString());
    launch("TRANTYPE", "dd.TRANTYPE", Goldens.seed("trantype.txt").toString());
    launch("TRANCATG", "dd.TRANCATG", Goldens.seed("trancatg.txt").toString());
    launch("DUSRSECJ");
    launch("ESDSRRDS");
    assertThat(
            jdbc.queryForObject(
                "SELECT SUM(acct_curr_bal) FROM account", java.math.BigDecimal.class))
        .isNotNull();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usrsec_rrds", Long.class)).isEqualTo(10);

    launch("READACCT");
    Path ds = dir.resolve("datasets");
    assertThat(Files.readAllBytes(ds.resolve("AWS.M2.CARDDEMO.ACCTDATA.VBPS")))
        .isEqualTo(Goldens.bytes(Goldens.batch("READACCT", "VBPS.bin")));
    for (String j : List.of("READCARD", "READXREF", "READCUST")) {
      JobExecution e = launch(j);
      assertThat(
              Files.readString(
                  Path.of(e.getJobParameters().getString("sysout")), StandardCharsets.ISO_8859_1))
          .as(j)
          .isEqualTo(Goldens.sysout(Goldens.b4(j, "SYSOUT.txt")));
    }
    launch("CBEXPORT", "dd.EXPFILE", dir.resolve("EXPDATA.dat").toString());
    assertThat(Files.readAllBytes(dir.resolve("EXPDATA.dat")))
        .isEqualTo(Goldens.bytes(Goldens.b4("CBEXPORT", "EXPDATA.dat")));
    launch(
        "CBIMPORT",
        "dd.EXPFILE",
        dir.resolve("EXPDATA.dat").toString(),
        "dd.TRNXOUT",
        dir.resolve("TRNXOUT.dat").toString());
    assertThat(Files.readAllBytes(dir.resolve("TRNXOUT.dat")))
        .isEqualTo(Goldens.bytes(Goldens.b4("CBIMPORT", "TRNXOUT.dat")));
    launch("DEFGDGB");
    launch(
        "DEFGDGD",
        "dd.STEP20.SYSUT1",
        Goldens.seed("trantype.txt").toString(),
        "dd.STEP40.SYSUT1",
        Goldens.seed("trancatg.txt").toString(),
        "dd.STEP60.SYSUT1",
        Goldens.seed("discgrp.txt").toString());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dl_gdg_base", Long.class)).isEqualTo(9);
  }
}
