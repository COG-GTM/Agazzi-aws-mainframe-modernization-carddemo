package com.carddemo.batch.interest.prtcatbl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.carddemo.batch.interest.B2TestApplication;
import com.carddemo.batch.interest.testsupport.FixtureDb;
import com.carddemo.batch.interest.testsupport.FixtureDb.Table;
import com.carddemo.batch.interest.testsupport.Generations;
import com.carddemo.batch.interest.testsupport.GoldenFixture;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** PRTCATBL: report layout, restart from STEP10R without re-running DELDEF/STEP05R. */
@SpringBootTest(classes = B2TestApplication.class)
class PrtcatblJobTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired JobLauncher launcher;
  @Autowired GenerationStore generations;

  @Autowired
  @Qualifier("prtcatblJob")
  Job job;

  @TempDir Path tmp;

  @Test
  void reportLineAppliesTheOutrecEditMask() {
    assertThat(
            PrtcatblJobConfig.reportLine(
                new TranCatBalanceRecord(1L, "01", 1, new BigDecimal("1234.56"), "")))
        .isEqualTo("00000000001 01 0001 000001234.56        ")
        .hasSize(40);
    assertThat(
            PrtcatblJobConfig.reportLine(
                new TranCatBalanceRecord(99L, "05", 12, new BigDecimal("-7.10"), "")))
        .isEqualTo("00000000099 05 0012 000000007.10        ");
  }

  @Test
  void restartsFromStep10r() throws Exception {
    FixtureDb db = new FixtureDb(jdbc);
    db.clear();
    db.load(Table.TRAN_CAT_BALANCE, GoldenFixture.load("b2-interest/PRTCATBL").input("TCATBALF"));
    Path dir = Files.createDirectory(tmp.resolve("spool"));
    Path rept = dir.resolve("TCATBALF.REPT");
    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-x------"));
    assumeFalse(Files.isWritable(dir), "running as root: permissions are not enforced");
    int gens = Generations.latest(generations, PrtcatblJobConfig.BKUP);
    JobParameters params =
        new JobParametersBuilder()
            .addString(PrtcatblJobConfig.REPT_PARAMETER, rept.toString())
            .addLong("run", System.nanoTime())
            .toJobParameters();

    JobExecution first = launcher.run(job, params);

    assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(first.getExitStatus().getExitCode()).isEqualTo("RC16");
    assertThat(Generations.latest(generations, PrtcatblJobConfig.BKUP)).isEqualTo(gens + 1);

    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
    JobExecution second = launcher.run(job, params);

    assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(second.getStepExecutions()).extracting("stepName").containsExactly("STEP10R");
    assertThat(Generations.latest(generations, PrtcatblJobConfig.BKUP)).isEqualTo(gens + 1);
    assertThat(Files.readAllLines(rept)).hasSize(8).allMatch(l -> l.length() == 40);
  }

  @Test
  void missingReportParameterFailsDeldef() throws Exception {
    JobExecution ex =
        launcher.run(
            job, new JobParametersBuilder().addLong("run", System.nanoTime()).toJobParameters());
    assertThat(ex.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(ex.getStepExecutions()).extracting("stepName").containsExactly("DELDEF");
  }
}
