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
  void defaultStorageIsDatabase() {
    PostingJobParameters p = PostingJobParameters.from(new JobParameters());
    assertThat(p.fileStorage()).isFalse();
    assertThat(List.of(p.storage())).containsExactly("db");
  }
}
