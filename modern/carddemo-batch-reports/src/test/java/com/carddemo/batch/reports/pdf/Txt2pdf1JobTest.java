package com.carddemo.batch.reports.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.reports.B3Fixtures;
import com.carddemo.batch.reports.ReportsTestApplication;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ReportsTestApplication.class)
class Txt2pdf1JobTest {

  @Autowired JobLauncher launcher;

  @Autowired
  @Qualifier(Txt2pdf1JobConfig.JOB)
  Job job;

  @TempDir Path dir;

  @Test
  void writesPdfIntoFreshDatasetDirFromExplicitDsn() throws Exception {
    Path stmt = B3Fixtures.B3.resolve("CREASTMT/expected/STMTPS.dat");
    Path out = dir.resolve("new-run");
    JobExecution e =
        launcher.run(
            job,
            new JobParametersBuilder()
                .addString("datasetDir", out.toString())
                .addString("dsn.STMTPS", stmt.toString())
                .toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(TextToPdf.extractLines(B3Fixtures.bytes(out.resolve("STMTPDF.dat")))).isNotEmpty();
  }
}
