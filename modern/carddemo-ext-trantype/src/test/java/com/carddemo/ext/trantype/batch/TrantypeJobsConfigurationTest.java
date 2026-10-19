package com.carddemo.ext.trantype.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.transaction.PlatformTransactionManager;

class TrantypeJobsConfigurationTest {

  @TempDir Path dir;

  private static StepExecution step(JobParameters p) {
    return new StepExecution("s", new JobExecution(new JobInstance(1L, "j"), p));
  }

  @Test
  void jobsAreNamedAfterTheLegacyJcl() {
    TrantypeJobsConfiguration cfg = new TrantypeJobsConfiguration();
    InMemoryTransactionTypeStore store = InMemoryTransactionTypeStore.seeded();
    JobRepository repo = mock(JobRepository.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    assertThat(cfg.mnttrdb2Job(repo, tx, new TranTypeMaintenance(store)).getName())
        .isEqualTo("MNTTRDB2");
    assertThat(cfg.tranextrJob(repo, tx, new TranTypeConsistencyCheck(store)).getName())
        .isEqualTo("TRANEXTR");
  }

  @Test
  void maintenanceTaskletWritesSysoutAndReturnCode() throws Exception {
    Path in = Files.writeString(dir.resolve("INPFILE"), "*comment\nX01bad\n");
    Path out = dir.resolve("SYSOUT");
    StepExecution se =
        step(
            new JobParametersBuilder()
                .addString("INPFILE", in.toString())
                .addString("SYSOUT", out.toString())
                .toJobParameters());
    StepContribution c = new StepContribution(se);
    TrantypeJobsConfiguration.maintenance(
        new TranTypeMaintenance(InMemoryTransactionTypeStore.seeded()),
        c,
        new ChunkContext(new StepContext(se)));
    assertThat(c.getExitStatus().getExitDescription()).isEqualTo("RC=0004");
    assertThat(c.getExitStatus().getExitCode())
        .isEqualTo(TrantypeJobsConfiguration.COMPLETED_WITH_WARNINGS);
    assertThat(Files.readAllLines(out)).first().isEqualTo("OPEN FILE OK");
    StepExecution missing =
        step(new JobParametersBuilder().addString("INPFILE", "/nope").toJobParameters());
    StepContribution c2 = new StepContribution(missing);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                TrantypeJobsConfiguration.maintenance(
                    new TranTypeMaintenance(InMemoryTransactionTypeStore.seeded()),
                    c2,
                    new ChunkContext(new StepContext(missing))))
        .hasMessage("RC=0012");
    assertThat(c2.getExitStatus().getExitDescription()).isEqualTo("RC=0012");
    assertThat(c2.getExitStatus().getExitCode()).isEqualTo("FAILED");
  }

  @Test
  void consistencyTaskletComparesExtracts() throws Exception {
    InMemoryTransactionTypeStore store = InMemoryTransactionTypeStore.seeded();
    TranTypeConsistencyCheck check = new TranTypeConsistencyCheck(store);
    Path tt =
        Files.writeString(dir.resolve("TRANTYPE"), String.join("", check.expectedTypeExtract()));
    Path tc = Files.write(dir.resolve("TRANCATG"), List.of());
    Path out = dir.resolve("SYSOUT");
    StepExecution se =
        step(
            new JobParametersBuilder()
                .addString("TRANTYPE", tt.toString())
                .addString("TRANCATG", tc.toString())
                .addString("SYSOUT", out.toString())
                .toJobParameters());
    StepContribution c = new StepContribution(se);
    TrantypeJobsConfiguration.consistency(check, c, new ChunkContext(new StepContext(se)));
    assertThat(c.getExitStatus().getExitDescription()).isEqualTo("RC=0004");
    assertThat(Files.readAllLines(out)).last().isEqualTo("INCONSISTENCIES: 18");
    StepExecution none = step(new JobParameters());
    StepContribution c2 = new StepContribution(none);
    TrantypeJobsConfiguration.consistency(check, c2, new ChunkContext(new StepContext(none)));
    assertThat(c2.getExitStatus().getExitDescription()).isEqualTo("RC=0000");
    assertThat(c2.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
  }
}
