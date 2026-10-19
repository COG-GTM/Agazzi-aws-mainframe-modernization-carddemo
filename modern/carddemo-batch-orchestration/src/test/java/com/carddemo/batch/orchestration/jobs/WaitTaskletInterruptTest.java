package com.carddemo.batch.orchestration.jobs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.batch.orchestration.core.JclStepFailure;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

class WaitTaskletInterruptTest {

  @Test
  void cancelledWaitIsAnS222Abend() {
    StepExecution se = new StepExecution("WAIT", new JobExecution(1L, new JobParameters()));
    ChunkContext chunk = mock(ChunkContext.class);
    StepContext ctx = mock(StepContext.class);
    when(chunk.getStepContext()).thenReturn(ctx);
    when(ctx.getJobParameters()).thenReturn(Map.of());
    when(ctx.getStepExecution()).thenReturn(se);
    var tasklet =
        new WaitTasklet(
            d -> {
              throw new InterruptedException();
            },
            "00000001");
    assertThatThrownBy(() -> tasklet.execute(null, chunk))
        .isInstanceOfSatisfying(
            JclStepFailure.class,
            f -> org.assertj.core.api.Assertions.assertThat(f.exitCode()).isEqualTo("ABEND-S222"));
    Thread.interrupted();
  }
}
