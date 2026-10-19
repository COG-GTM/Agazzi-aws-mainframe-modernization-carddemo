package com.carddemo.batch.orchestration.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.task.TaskExecutor;

class AsyncJobSubmitterTest {

  private static AsyncJobSubmitter submitter(TaskExecutor executor, Job job) throws Exception {
    RegisteredJobs jobs = mock(RegisteredJobs.class);
    when(jobs.find("J")).thenReturn(Optional.of(job));
    JobRepository repo = mock(JobRepository.class);
    when(repo.createJobExecution(any(), any()))
        .thenAnswer(i -> new JobExecution(new JobInstance(1L, "J"), 1L, new JobParameters()));
    return new AsyncJobSubmitter(jobs, repo, executor);
  }

  @Test
  void inlineFailureIsCountedOnceAndQueuedJobsAreAwaited() throws Exception {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("J");
    when(job.getJobParametersValidator())
        .thenReturn(new org.springframework.batch.core.job.DefaultJobParametersValidator());
    org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(job).execute(any());
    // Inline executor that propagates the task failure (like caller-runs).
    AsyncJobSubmitter inline = submitter(Runnable::run, job);
    // Whether the launcher rethrows the job failure or not, it must be counted exactly once.
    org.assertj.core.api.Assertions.catchThrowable(() -> inline.submit("J", new JobParameters()));
    assertThat(inline.awaitSubmitted(Duration.ZERO)).isTrue();

    List<Runnable> queue = new ArrayList<>();
    AsyncJobSubmitter queued = submitter(queue::add, job);
    queued.submit("J", new JobParameters());
    assertThat(queued.awaitSubmitted(Duration.ofMillis(10))).as("queued, not started").isFalse();
    try {
      queue.get(0).run();
    } catch (RuntimeException expected) {
      // the job failure itself
    }
    assertThat(queued.awaitSubmitted(Duration.ZERO)).isTrue();
  }

  @Test
  void rejectedSubmissionIsNotLeftInFlight() throws Exception {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("J");
    when(job.getJobParametersValidator())
        .thenReturn(new org.springframework.batch.core.job.DefaultJobParametersValidator());
    AsyncJobSubmitter rejecting =
        submitter(
            r -> {
              throw new org.springframework.core.task.TaskRejectedException("full");
            },
            job);
    // TaskExecutorJobLauncher marks a rejected execution FAILED (and may rethrow).
    org.assertj.core.api.Assertions.catchThrowable(
        () -> rejecting.submit("J", new JobParameters()));
    assertThat(rejecting.awaitSubmitted(Duration.ZERO)).isTrue();
  }
}
