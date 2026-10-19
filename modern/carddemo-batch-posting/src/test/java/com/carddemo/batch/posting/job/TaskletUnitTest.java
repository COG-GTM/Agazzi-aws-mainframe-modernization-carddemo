package com.carddemo.batch.posting.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.carddemo.batch.posting.support.ReturnCodes;
import com.carddemo.batch.posting.support.Sysout;
import com.carddemo.platform.files.FileSystemGenerationStore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.beans.factory.ObjectProvider;

class TaskletUnitTest {

  @TempDir Path dir;

  @Test
  @SuppressWarnings("unchecked")
  void tranbkpStep10IsBypassedWhenAPreviousStepEndedAboveRc4() {
    JobExecution job = MetaDataInstanceFactory.createJobExecution();
    StepExecution step05 = job.createStepExecution("STEP05");
    step05.setId(1L);
    StepExecution step10 = job.createStepExecution("STEP10");
    step10.setId(2L);
    PostingTasklets.TranbkpStep10 t =
        new PostingTasklets.TranbkpStep10(
            new PostingDatasetsFactory(
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class)));
    step05.getExecutionContext().putInt(ReturnCodes.RC_KEY, 4);
    assertThat(t.bypass(step10)).isFalse();
    step05.getExecutionContext().putInt(ReturnCodes.RC_KEY, 8);
    assertThat(t.bypass(step10)).isTrue();
    assertThat(ReturnCodes.maxPreviousRc(step10)).isEqualTo(8);
  }

  @Test
  void dalyrejsStep05FlagsAGdgBaseWithTheWrongLimit() {
    Sysout sysout = new Sysout("IDCAMS");
    int rc =
        new PostingTasklets.DalyrejsStep05(new FileSystemGenerationStore(dir, 3))
            .run(
                PostingJobParameters.from(new JobParameters()),
                sysout,
                MetaDataInstanceFactory.createStepExecution());
    assertThat(rc).isEqualTo(8);
    assertThat(sysout.lines()).anyMatch(l -> l.contains("LIMIT IS 3"));
  }

  @Test
  void jobExitStatusIsTheHighestStepConditionCode() {
    JobExecution job = MetaDataInstanceFactory.createJobExecution();
    job.setStatus(org.springframework.batch.core.BatchStatus.COMPLETED);
    job.createStepExecution("STEP20").getExecutionContext().putInt(ReturnCodes.RC_KEY, 12);
    job.createStepExecution("STEP30").getExecutionContext().putInt(ReturnCodes.RC_KEY, 0);
    job.setExitStatus(org.springframework.batch.core.ExitStatus.COMPLETED);
    new MaxConditionCodeListener().afterJob(job);
    assertThat(job.getExitStatus().getExitCode()).isEqualTo("RC0012");
    assertThat(ReturnCodes.processExitCode(job.getExitStatus().getExitCode())).isEqualTo(12);
  }

  @Test
  void generationIsCatalogedOnlyWhenTheTransactionCommits() {
    FileSystemGenerationStore store = new FileSystemGenerationStore(dir);
    org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
    try {
      Generations.commitWithTransaction(store, store.allocate("ROLLBACK"));
      Generations.commitWithTransaction(store, store.allocate("COMMIT"));
      var syncs =
          org.springframework.transaction.support.TransactionSynchronizationManager
              .getSynchronizations();
      syncs
          .get(0)
          .afterCompletion(
              org.springframework.transaction.support.TransactionSynchronization
                  .STATUS_ROLLED_BACK);
      syncs
          .get(1)
          .afterCompletion(
              org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED);
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager
          .clearSynchronization();
    }
    assertThat(store.list("ROLLBACK")).isEmpty();
    assertThat(store.list("COMMIT")).hasSize(1);
    Generations.commitWithTransaction(store, store.allocate("NOTX"));
    assertThat(store.list("NOTX")).hasSize(1);
  }

  @Test
  void defaultStorageIsDatabase() {
    PostingJobParameters p = PostingJobParameters.from(new JobParameters());
    assertThat(p.fileStorage()).isFalse();
    assertThat(List.of(p.storage())).containsExactly("db");
  }
}
