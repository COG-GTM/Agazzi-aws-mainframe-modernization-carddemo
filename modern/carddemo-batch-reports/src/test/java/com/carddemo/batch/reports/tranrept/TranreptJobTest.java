package com.carddemo.batch.reports.tranrept;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.reports.B3Fixtures;
import com.carddemo.batch.reports.ReportsTestApplication;
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
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ReportsTestApplication.class)
class TranreptJobTest {

  @Autowired JobLauncher launcher;

  @Autowired
  @Qualifier(TranreptJobConfig.JOB)
  Job job;

  @TempDir Path dir;

  @BeforeEach
  void inputs() {
    B3Fixtures.materialise("TRANREPT", dir);
  }

  @Test
  void runsAllJclStepsWithJobParameterDates() throws Exception {
    Files.delete(dir.resolve("DATEPARM.dat"));
    JobExecution e =
        launcher.run(
            job, B3Fixtures.params(dir, "startDate", "2022-07-18", "endDate", "2022-07-18"));
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(e.getStepExecutions().stream().map(StepExecution::getStepName))
        .containsExactly("STEP05R-REPROC", "STEP05R-SORT", "STEP10R");
    List<String> report = B3Fixtures.lines(dir.resolve("TRANREPT.dat"));
    assertThat(report).hasSize(532).allMatch(l -> l.length() == 133);
    assertThat(report.get(0)).contains("Date Range: 2022-07-18 to 2022-07-18");
    assertThat(report.get(report.size() - 1)).startsWith("Grand Total");
  }

  @Test
  void dateParmDatasetIsUsedWithoutJobParameters() throws Exception {
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "dateparm"));
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(B3Fixtures.lines(dir.resolve("TRANREPT.dat")).get(0))
        .contains("Date Range: 2022-07-01 to 2022-07-31");
  }

  @Test
  void emptyDateParmSortsWithSymnamesAndReportsNothing() throws Exception {
    Files.writeString(dir.resolve("DATEPARM.dat"), "");
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "nodates"));
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(B3Fixtures.returnCodes(e))
        .containsEntry("STEP05R-SORT", 0)
        .containsEntry("STEP10R", 0);
    assertThat(B3Fixtures.bytes(dir.resolve("TRANREPT.dat"))).isEmpty();
  }

  @Test
  void missingDateParmWithoutJobParametersAbends() throws Exception {
    Files.delete(dir.resolve("DATEPARM.dat"));
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "nodateparm"));
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("ABEND");
    assertThat(B3Fixtures.returnCodes(e)).containsEntry("STEP05R-SORT", 16);
  }

  @Test
  void missingTransactDatasetAbends() throws Exception {
    Files.delete(dir.resolve("TRANSACT.dat"));
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "nofile"));
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("ABEND");
    assertThat(e.getExitStatus().getExitDescription()).contains("DATASET NOT FOUND");
    assertThat(B3Fixtures.returnCodes(e)).containsEntry("STEP05R-REPROC", 16);
  }

  @Test
  void emptyTransactFileGivesEmptyReport() throws Exception {
    Files.writeString(dir.resolve("TRANSACT.dat"), "");
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "empty"));
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(B3Fixtures.bytes(dir.resolve("TRANREPT.dat"))).isEmpty();
  }

  @Test
  void invalidCardAbendsAndRestartResumesFromStep10r() throws Exception {
    Path xref = dir.resolve("CARDXREF.dat");
    byte[] good = Files.readAllBytes(xref);
    Files.writeString(xref, "");
    JobParameters p = B3Fixtures.params(dir, "run", "restart");
    JobExecution first = launcher.run(job, p);
    assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(first.getExitStatus().getExitCode()).isEqualTo("ABEND");
    assertThat(first.getExitStatus().getExitDescription()).contains("INVALID CARD NUMBER");
    assertThat(B3Fixtures.returnCodes(first))
        .containsEntry("STEP05R-REPROC", 0)
        .containsEntry("STEP05R-SORT", 0)
        .containsEntry("STEP10R", 16);

    Files.write(xref, good);
    JobExecution restart = launcher.run(job, p);
    assertThat(restart.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restart.getStepExecutions().stream().map(StepExecution::getStepName))
        .containsExactly("STEP10R");
    assertThat(B3Fixtures.bytes(dir.resolve("TRANREPT.dat")))
        .isEqualTo(B3Fixtures.bytes(B3Fixtures.B3.resolve("TRANREPT/expected/TRANREPT.dat")));
  }

  @Test
  void invalidTypeCategoryAndMalformedAmountAbend() throws Exception {
    Files.writeString(dir.resolve("TRANTYPE.dat"), "");
    assertThat(
            launcher
                .run(job, B3Fixtures.params(dir, "run", "type"))
                .getExitStatus()
                .getExitDescription())
        .contains("INVALID TRANSACTION TYPE");
    B3Fixtures.materialise("TRANREPT", dir);
    Files.writeString(dir.resolve("TRANCATG.dat"), "");
    assertThat(
            launcher
                .run(job, B3Fixtures.params(dir, "run", "catg"))
                .getExitStatus()
                .getExitDescription())
        .contains("INVALID TRAN CATG KEY");
    B3Fixtures.materialise("TRANREPT", dir);
    List<String> tran = B3Fixtures.lines(dir.resolve("TRANSACT.dat"));
    String bad = tran.get(0).substring(0, 132) + "ABCDEFGHIJK" + tran.get(0).substring(143);
    Files.writeString(dir.resolve("TRANSACT.dat"), bad + "\n");
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "run", "malformed"));
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("ABEND");
    assertThat(e.getExitStatus().getExitDescription()).contains("malformed TRAN-AMT");
  }

  @Test
  void dateParmRecordLayout() {
    Cbtrn03cService.DateParm d = Cbtrn03cService.DateParm.parse("2022-01-01 2022-12-31");
    assertThat(d.startDate()).isEqualTo("2022-01-01");
    assertThat(d.endDate()).isEqualTo("2022-12-31");
    assertThat(d.toRecord()).isEqualTo("2022-01-01 2022-12-31");
    assertThat(Cbtrn03cService.amount(" ".repeat(350))).isZero();
  }
}
