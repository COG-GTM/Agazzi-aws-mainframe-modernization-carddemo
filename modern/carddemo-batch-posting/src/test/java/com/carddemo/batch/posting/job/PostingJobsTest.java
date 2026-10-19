package com.carddemo.batch.posting.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.posting.dataset.FileDatasets;
import com.carddemo.batch.posting.dataset.LineSequentialFile;
import com.carddemo.batch.posting.golden.Golden;
import com.carddemo.batch.posting.testapp.FileStorageTestApp;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.GenerationStore.Generation;
import com.carddemo.copybook.CoreCodecs;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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

/** The B1 Spring Batch jobs end to end on file storage (exit statuses, abend, restart). */
@SpringBootTest(classes = FileStorageTestApp.class)
class PostingJobsTest {

  private static final AtomicLong RUN = new AtomicLong(System.nanoTime());

  @TempDir Path dir;

  @Autowired JobLauncher launcher;
  @Autowired GenerationStore generations;

  @Autowired
  @Qualifier(PostingJobsConfiguration.POSTTRAN)
  Job posttran;

  @Autowired
  @Qualifier(PostingJobsConfiguration.CBTRN01C)
  Job cbtrn01c;

  @Autowired
  @Qualifier(PostingJobsConfiguration.DALYREJS)
  Job dalyrejs;

  @Autowired
  @Qualifier(PostingJobsConfiguration.TRANBKP)
  Job tranbkp;

  @Autowired
  @Qualifier(PostingJobsConfiguration.COMBTRAN)
  Job combtran;

  @Autowired
  @Qualifier(PostingJobsConfiguration.TRANIDX)
  Job tranidx;

  private JobParametersBuilder params() {
    return new JobParametersBuilder()
        .addString("storage", "file")
        .addString("datasetDir", dir.toString())
        .addString("sysout", dir.resolve("SYSOUT.txt").toString())
        .addLong("run.id", RUN.incrementAndGet());
  }

  private void edgeInputs() {
    Path in = Golden.b1("POSTTRAN-EDGE").resolve("input");
    Golden.copy(in.resolve("dailytran.txt"), dir, FileDatasets.DALYTRAN);
    Golden.copy(in.resolve("cardxref.txt"), dir, FileDatasets.CARDXREF);
    Golden.copy(in.resolve("acctdata.txt"), dir, FileDatasets.ACCTDATA);
    Golden.copy(in.resolve("tcatbal.txt"), dir, FileDatasets.TCATBALF);
  }

  private List<String> current(String base, int lrecl) throws Exception {
    Generation g = generations.resolve(base, 0).orElseThrow();
    try (InputStream in = generations.openInput(g)) {
      return LineSequentialFile.read(in, lrecl, g.dsn());
    }
  }

  private List<String> file(String ds, int lrecl) {
    return LineSequentialFile.read(dir.resolve(ds + ".dat"), lrecl);
  }

  private static StepExecution step(JobExecution e, String name) {
    return e.getStepExecutions().stream()
        .filter(s -> s.getStepName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void posttranPostsAndRejectsWithRc4() throws Exception {
    edgeInputs();
    JobExecution e = launcher.run(posttran, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("RC0004");
    assertThat(e.getStepExecutions())
        .extracting(StepExecution::getStepName)
        .containsExactly("STEP15");
    StepExecution s = step(e, "STEP15");
    assertThat(s.getExecutionContext().getInt("returnCode")).isEqualTo(4);
    assertThat(s.getExecutionContext().getInt("rejected")).isEqualTo(6);
    Path golden = Golden.b1("POSTTRAN-EDGE");
    Golden.assertSameRejects(
        Golden.expected(golden, "DALYREJS", 430), current(Generations.DALYREJS, 430));
    Golden.assertSameRecords(
        "ACCTDATA",
        Golden.expected(golden, "ACCTDATA", 300),
        file("ACCTDATA", 300),
        CoreCodecs.ACCOUNT);
    Golden.assertSameRecords(
        "TRANSACT",
        Golden.expected(golden, "TRANSACT", 350),
        file("TRANSACT", 350),
        CoreCodecs.TRANSACTION);
    List<String> sysout = Files.readAllLines(dir.resolve("SYSOUT.txt"));
    assertThat(Golden.rtrim(sysout))
        .isEqualTo(Golden.rtrim(Files.readAllLines(golden.resolve("expected/SYSOUT.txt"))));
  }

  @Test
  void posttranEmptyInputEndsRc0WithEmptyRejects() throws Exception {
    edgeInputs();
    Files.writeString(dir.resolve("DALYTRAN.dat"), "");
    byte[] accounts = Files.readAllBytes(dir.resolve("ACCTDATA.dat"));
    JobExecution e = launcher.run(posttran, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
    assertThat(current(Generations.DALYREJS, 430)).isEmpty();
    assertThat(Files.readAllBytes(dir.resolve("ACCTDATA.dat"))).isEqualTo(accounts);
  }

  @Test
  void posttranMalformedInputAbendsAndRestartsFromFailedStep() throws Exception {
    edgeInputs();
    Path dalytran = dir.resolve("DALYTRAN.dat");
    List<String> good = Files.readAllLines(dalytran);
    String bad = good.get(1).substring(0, 132) + "00000ABC10{" + good.get(1).substring(143);
    Files.write(dalytran, List.of(good.get(0), bad));
    byte[] accounts = Files.readAllBytes(dir.resolve("ACCTDATA.dat"));
    int rejectGenerations = generations.list(Generations.DALYREJS).size();
    JobParameters p = params().toJobParameters();

    JobExecution failed = launcher.run(posttran, p);
    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(failed.getExitStatus().getExitCode()).isEqualTo("ABEND-U0999");
    assertThat(step(failed, "STEP15").getExecutionContext().getInt("returnCode")).isEqualTo(16);
    assertThat(generations.list(Generations.DALYREJS)).hasSize(rejectGenerations);
    assertThat(Files.readAllBytes(dir.resolve("ACCTDATA.dat"))).isEqualTo(accounts);
    assertThat(Files.exists(dir.resolve("TRANSACT.dat"))).isFalse();

    Files.write(dalytran, good);
    JobExecution restarted = launcher.run(posttran, p);
    assertThat(restarted.getJobInstance().getInstanceId())
        .isEqualTo(failed.getJobInstance().getInstanceId());
    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getExitStatus().getExitCode()).isEqualTo("RC0004");
    assertThat(generations.list(Generations.DALYREJS)).hasSize(rejectGenerations + 1);
  }

  @Test
  void posttranAbendsOnDuplicateTransactionId() throws Exception {
    edgeInputs();
    Files.copy(
        Golden.b1("POSTTRAN-EDGE").resolve("expected/TRANSACT.dat"), dir.resolve("TRANSACT.dat"));
    JobExecution e = launcher.run(posttran, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("ABEND-U0999");
    assertThat(Files.readString(dir.resolve("SYSOUT.txt")))
        .contains(
            "ERROR WRITING TO TRANSACTION FILE", "FILE STATUS IS: NNNN0022", "ABENDING PROGRAM");
  }

  @Test
  void posttranAbendsWhenMasterFileIsMissing() throws Exception {
    edgeInputs();
    Files.delete(dir.resolve("ACCTDATA.dat"));
    JobExecution e = launcher.run(posttran, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("ABEND-U0999");
    assertThat(e.getExitStatus().getExitDescription()).contains("ERROR OPENING ACCTDATA");
  }

  @Test
  void tranbkpBacksUpThenEmptiesTheCluster() throws Exception {
    Path golden = Golden.b1("TRANBKP");
    Files.copy(Golden.b1("POSTTRAN").resolve("expected/TRANSACT.dat"), dir.resolve("TRANSACT.dat"));
    Files.writeString(dir.resolve(PostingTasklets.AIX_FILE), "x\n");
    JobExecution e = launcher.run(tranbkp, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(e.getStepExecutions())
        .extracting(StepExecution::getStepName)
        .containsExactly("STEP05R", "STEP05", "STEP10");
    Golden.assertSameRecords(
        "TRANBKUP",
        Golden.expected(golden, "TRANBKUP", 350),
        current(Generations.TRANSACT_BKUP, 350),
        CoreCodecs.TRANSACTION);
    assertThat(file("TRANSACT", 350)).isEmpty();
    assertThat(Files.exists(dir.resolve(PostingTasklets.AIX_FILE))).isFalse();
  }

  @Test
  void combtranMergesBackupAndSystranLikeGolden() throws Exception {
    Path golden = Golden.batch("COMBTRAN");
    JobExecution e =
        launcher.run(
            combtran,
            params()
                .addString(
                    "tranbkup",
                    Golden.batch("POSTTRAN").resolve("expected/TRANSACT.dat").toString())
                .addString(
                    "systran", Golden.batch("INTCALC").resolve("expected/SYSTRAN.dat").toString())
                .toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(e.getStepExecutions())
        .extracting(StepExecution::getStepName)
        .containsExactly("STEP05R", "STEP10");
    assertThat(current(Generations.TRANSACT_COMBINED, 350))
        .isEqualTo(Golden.expected(golden, "COMBINED", 350));
    Golden.assertSameRecords(
        "TRANSACT",
        Golden.expected(golden, "TRANSACT", 350),
        file("TRANSACT", 350),
        CoreCodecs.TRANSACTION);
  }

  @Test
  void combtranReadsGenerationZeroAndRestartsFromStep10() throws Exception {
    Generations.writeNew(
        generations,
        Generations.TRANSACT_BKUP,
        Golden.expected(Golden.batch("POSTTRAN"), "TRANSACT", 350));
    Generations.writeNew(
        generations, Generations.SYSTRAN, Golden.expected(Golden.batch("INTCALC"), "SYSTRAN", 350));
    List<String> combined = Golden.expected(Golden.batch("COMBTRAN"), "COMBINED", 350);
    Files.write(dir.resolve("TRANSACT.dat"), List.of(combined.get(0)), StandardCharsets.ISO_8859_1);
    int before = generations.list(Generations.TRANSACT_COMBINED).size();
    JobParameters p = params().toJobParameters();

    JobExecution failed = launcher.run(combtran, p);
    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(failed.getExitStatus().getExitCode()).isEqualTo("ABEND-RC0012");
    assertThat(step(failed, "STEP05R").getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(step(failed, "STEP10").getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(step(failed, "STEP10").getExecutionContext().getInt("returnCode")).isEqualTo(12);

    Files.delete(dir.resolve("TRANSACT.dat"));
    JobExecution restarted = launcher.run(combtran, p);
    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getStepExecutions())
        .extracting(StepExecution::getStepName)
        .containsExactly("STEP10");
    assertThat(generations.list(Generations.TRANSACT_COMBINED)).hasSize(before + 1);
    assertThat(file("TRANSACT", 350)).hasSize(combined.size());
  }

  @Test
  void combtranAbendsWhenGenerationIsMissing() throws Exception {
    JobExecution e =
        launcher.run(
            combtran,
            params()
                .addString("tranbkup", dir.resolve("missing.dat").toString())
                .toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("ABEND-U0999");
  }

  @Test
  void tranidxBuildsTheAlternateIndexOnProcessingTimestamp() throws Exception {
    Files.copy(
        Golden.batch("COMBTRAN").resolve("expected/TRANSACT.dat"), dir.resolve("TRANSACT.dat"));
    JobExecution e = launcher.run(tranidx, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(e.getStepExecutions())
        .extracting(StepExecution::getStepName)
        .containsExactly("STEP20", "STEP25", "STEP30");
    List<String> aix = Files.readAllLines(dir.resolve(PostingTasklets.AIX_FILE));
    assertThat(aix).hasSize(307).isSorted();
    assertThat(aix.get(0)).hasSize(42);
  }

  @Test
  void dalyrejsDefinesTheGdgBase() throws Exception {
    JobExecution e = launcher.run(dalyrejs, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
    assertThat(Files.readString(dir.resolve("SYSOUT.txt"))).contains("LIMIT(5) SCRATCH");
  }

  @Test
  void cbtrn01cReportMatchesGoldenSysout() throws Exception {
    Path in = Golden.b1("POSTTRAN-EDGE").resolve("input");
    Golden.copy(in.resolve("dailytran.txt"), dir, FileDatasets.DALYTRAN);
    Golden.copy(in.resolve("cardxref.txt"), dir, FileDatasets.CARDXREF);
    Golden.copy(in.resolve("acctdata.txt"), dir, FileDatasets.ACCTDATA);
    Golden.copy(Golden.seed("custdata.txt"), dir, FileDatasets.CUSTDATA);
    Golden.copy(Golden.seed("carddata.txt"), dir, FileDatasets.CARDDATA);
    JobExecution e = launcher.run(cbtrn01c, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            Golden.rtrim(
                Files.readAllLines(dir.resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1)))
        .isEqualTo(
            Golden.rtrim(
                Files.readAllLines(
                    Golden.b1("CBTRN01C").resolve("expected/SYSOUT.txt"),
                    StandardCharsets.ISO_8859_1)));
  }

  @Test
  void fileStorageRequiresDatasetDir() throws Exception {
    JobExecution e =
        launcher.run(
            cbtrn01c,
            new JobParametersBuilder()
                .addString("storage", "file")
                .addLong("run.id", RUN.incrementAndGet())
                .toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("FAILED");
  }
}
