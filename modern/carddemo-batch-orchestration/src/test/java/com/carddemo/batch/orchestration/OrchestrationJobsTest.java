package com.carddemo.batch.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.orchestration.core.JclReturnCodes;
import com.carddemo.batch.orchestration.maintenance.CicsFiles;
import com.carddemo.contracts.port.MaintenanceModePort;
import com.carddemo.contracts.port.ReportJobLauncherPort;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportRequest;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class OrchestrationJobsTest extends BatchTestBase {

  @Autowired MaintenanceModePort maintenance;
  @Autowired JdbcTemplate jdbc;
  @Autowired ReportJobLauncherPort reports;

  @Test
  void closefilAndOpenfilToggleTheFiveCicsFilesInTheSharedTable() throws Exception {
    JobExecution close = run("CLOSEFIL");
    assertThat(close.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(stepNames(close)).containsExactly("CLCIFIL");
    assertThat(maintenance.closedFiles())
        .containsExactlyInAnyOrderElementsOf(CicsFiles.BATCH_WINDOW);
    assertThat(maintenance.inMaintenance()).isTrue();
    assertThat(maintenance.isOpen("TRANSACT")).isFalse();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cics_file_status", Integer.class))
        .isEqualTo(5);

    JobExecution open = run("OPENFIL");
    assertThat(stepNames(open)).containsExactly("OPCIFIL");
    assertThat(maintenance.closedFiles()).isEmpty();
    assertThat(JclReturnCodes.of(open)).isZero();
  }

  @Test
  void closefilAcceptsAFileListAndRejectsMalformedNamesWithRc8() throws Exception {
    run("CLOSEFIL", params().addString("files", "acctdat").toJobParameters());
    assertThat(maintenance.closedFiles()).containsExactly("ACCTDAT");
    run("OPENFIL", params().addString("files", "ACCTDAT").toJobParameters());

    JobExecution bad =
        run("CLOSEFIL", params().addString("files", "TRANSACT,NOT A FILE").toJobParameters());
    assertThat(bad.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(step(bad, "CLCIFIL").getExitStatus().getExitCode()).isEqualTo("RC08");
    assertThat(JclReturnCodes.of(bad)).isEqualTo(8);
    assertThat(maintenance.closedFiles()).isEmpty();
  }

  @Test
  void waitstepWaitsTheSysinCentiseconds() throws Exception {
    JobExecution e = run("WAITSTEP");
    assertThat(stepNames(e)).containsExactly("WAIT");
    assertThat(waits.waits).containsExactly(Duration.ofSeconds(36));
    assertThat(step(e, "WAIT").getExecutionContext().getLong("centiseconds")).isEqualTo(3600);

    run("WAITSTEP", params().addString("centiseconds", "00000150").toJobParameters());
    assertThat(waits.waits).last().isEqualTo(Duration.ofMillis(1500));
  }

  @Test
  void intrdrj1CopiesThenSubmitsIntrdrj2Asynchronously() throws Exception {
    Files.writeString(dataset("AWS.M2.CARDEMO.FTP.TEST"), "REC1\nREC2\n");
    JobExecution j1 = run("INTRDRJ1");
    assertThat(j1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(stepNames(j1)).containsExactly("IDCAMS", "STEP01");
    assertThat(step(j1, "IDCAMS").getExecutionContext().getLong("records")).isEqualTo(2);
    long submitted = step(j1, "STEP01").getExecutionContext().getLong("submitted.executionId");
    JobExecution j2 = awaitEnd(submitted);
    assertThat(j2.getJobInstance().getJobName()).isEqualTo("INTRDRJ2");
    assertThat(j2.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(Files.readString(dataset("AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR")))
        .isEqualTo("REC1\nREC2\n");
  }

  @Test
  void intrdrj1EmptyInputCopiesZeroRecords() throws Exception {
    Files.writeString(dataset("AWS.M2.CARDEMO.FTP.TEST"), "");
    JobExecution j1 = run("INTRDRJ1");
    assertThat(j1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(step(j1, "IDCAMS").getExecutionContext().getLong("records")).isZero();
    awaitEnd(step(j1, "STEP01").getExecutionContext().getLong("submitted.executionId"));
    assertThat(Files.size(dataset("AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR"))).isZero();
  }

  @Test
  void intrdrj1MissingInputEndsRc12AndDoesNotSubmit() throws Exception {
    JobExecution j1 = run("INTRDRJ1");
    assertThat(j1.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(stepNames(j1)).containsExactly("IDCAMS");
    assertThat(step(j1, "IDCAMS").getExitStatus().getExitCode()).isEqualTo("RC12");
    assertThat(JclReturnCodes.of(j1)).isEqualTo(12);
  }

  @Test
  void intrdrj1RestartsFromTheFailedStep() throws Exception {
    var p = params().toJobParameters();
    JobExecution first = run("INTRDRJ1", p);
    assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
    Files.writeString(dataset("AWS.M2.CARDEMO.FTP.TEST"), "R\n");
    JobExecution second = run("INTRDRJ1", p);
    assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(second.getJobInstance().getId()).isEqualTo(first.getJobInstance().getId());
    awaitEnd(step(second, "STEP01").getExecutionContext().getLong("submitted.executionId"));
  }

  @Test
  void ftpjclPutsTheDatasetThroughTheTransferPort() throws Exception {
    Files.writeString(dataset("AWS.M2.CARDEMO.FTP.TEST"), "HELLO\n");
    JobExecution e = run("FTPJCL");
    assertThat(stepNames(e)).containsExactly("STEP1");
    Path remote = props.getTransfer().getLocalDir().resolve("ftpfolder/welcome.txt");
    assertThat(Files.readString(remote)).isEqualTo("HELLO\n");
  }

  @Test
  void ftpjclMissingDatasetOrBadRemoteNameEndsRc8() throws Exception {
    JobExecution missing = run("FTPJCL");
    assertThat(step(missing, "STEP1").getExitStatus().getExitCode()).isEqualTo("RC08");
    Files.writeString(dataset("AWS.M2.CARDEMO.FTP.TEST"), "X\n");
    JobExecution bad = run("FTPJCL", params().addString("remoteName", "../etc").toJobParameters());
    assertThat(step(bad, "STEP1").getExitStatus().getExitCode()).isEqualTo("RC08");
    JobExecution badDsn = run("FTPJCL", params().addString("dataset", "bad dsn").toJobParameters());
    assertThat(step(badDsn, "STEP1").getExitStatus().getExitCode()).isEqualTo("ABEND-S813");
    assertThat(JclReturnCodes.of(badDsn)).isEqualTo(16);
  }

  @Test
  void cbadmcdjLoadsTheCsdGroupIdempotently() throws Exception {
    JobExecution e = run("CBADMCDJ");
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    int defined = step(e, "STEP1").getExecutionContext().getInt("defined");
    assertThat(step(e, "STEP1").getExecutionContext().getInt("duplicates")).isPositive();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cics_csd_definition", Integer.class))
        .isEqualTo(defined);
    assertThat(
            jdbc.queryForObject(
                "SELECT attributes FROM cics_csd_definition WHERE resource_type='TRANSACTION' AND resource_name='CCDM'",
                String.class))
        .contains("PROGRAM(COADM00C)");
    assertThat(
            jdbc.queryForObject(
                "SELECT attributes FROM cics_csd_definition WHERE resource_type='LIBRARY'",
                String.class))
        .contains("DSNAME01(AWS.M2.CARDDEMO.LOADLIB)");
    run("CBADMCDJ");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cics_csd_definition", Integer.class))
        .isEqualTo(defined);
  }

  @Test
  void cbadmcdjMalformedOrMissingSysinEndsRc12() throws Exception {
    Path bad = dataset("BAD.CSD");
    Files.writeString(
        bad, "  DEFINE PROGRAM(TOOLONGNAME) GROUP(CARDDEMO)\n", StandardCharsets.UTF_8);
    JobExecution e = run("CBADMCDJ", params().addString("sysin", bad.toString()).toJobParameters());
    assertThat(step(e, "STEP1").getExitStatus().getExitCode()).isEqualTo("RC12");
    JobExecution missing =
        run("CBADMCDJ", params().addString("sysin", dataset("NOPE").toString()).toJobParameters());
    assertThat(step(missing, "STEP1").getExitStatus().getExitCode()).isEqualTo("RC12");
  }

  @Test
  void reportLauncherSubmitsTranreptByNameAndTracksStatus() throws Exception {
    var handle =
        reports.launch(
            new ReportRequest(
                ReportType.CUSTOM,
                LocalDate.of(2022, 7, 1),
                LocalDate.of(2022, 7, 31),
                "ADMIN001"));
    assertThat(handle.jobName()).isEqualTo("TRANREPT");
    JobExecution e = awaitEnd(handle.executionId());
    assertThat(e.getJobParameters().getString("startDate")).isEqualTo("2022-07-01");
    assertThat(reports.status(handle.executionId()))
        .get()
        .extracting(h -> h.status())
        .isEqualTo("COMPLETED");
    assertThat(reports.status(-1)).isEmpty();
    assertThat(RunLog.runs()).containsExactly("TRANREPT");
  }
}
