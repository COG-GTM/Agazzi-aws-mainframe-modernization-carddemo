package com.carddemo.batch.orchestration.launch;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.core.task.TaskExecutor;

/**
 * Internal reader ({@code SYSOUT=(A,INTRDR)}): submit a job by name and return immediately. Uses a
 * private asynchronous {@code JobLauncher} so the application keeps a single {@code JobLauncher}
 * bean.
 */
public class AsyncJobSubmitter {

  private final RegisteredJobs jobs;
  private final TaskExecutorJobLauncher launcher;
  private int inFlight;

  public AsyncJobSubmitter(RegisteredJobs jobs, JobRepository repository, TaskExecutor executor) {
    this.jobs = jobs;
    this.launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(repository);
    launcher.setTaskExecutor(
        task -> {
          // Counted from acceptance (queued or running) so a CLI drain never misses it.
          synchronized (this) {
            inFlight++;
          }
          AtomicBoolean finished = new AtomicBoolean();
          Runnable doneOnce =
              () -> {
                if (finished.compareAndSet(false, true)) {
                  done();
                }
              };
          try {
            executor.execute(
                () -> {
                  try {
                    task.run();
                  } finally {
                    doneOnce.run();
                  }
                });
          } catch (RuntimeException e) {
            doneOnce.run();
            throw e;
          }
        });
    try {
      launcher.afterPropertiesSet();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private synchronized void done() {
    inFlight--;
    notifyAll();
  }

  /** CLI mode: like JES, the submitted jobs keep running after the submitter ends. */
  public synchronized boolean awaitSubmitted(Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (inFlight > 0) {
      long left = deadline - System.nanoTime();
      if (left <= 0) {
        return false;
      }
      wait(Math.max(1, left / 1_000_000));
    }
    return true;
  }

  public JobExecution submit(String jobName, JobParameters params)
      throws NoSuchJobException,
          JobExecutionAlreadyRunningException,
          JobRestartException,
          JobInstanceAlreadyCompleteException,
          JobParametersInvalidException {
    Job job = jobs.find(jobName).orElseThrow(() -> new NoSuchJobException(jobName));
    return launcher.run(job, params);
  }
}
