package com.carddemo.batch.orchestration.launch;

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

  public AsyncJobSubmitter(RegisteredJobs jobs, JobRepository repository, TaskExecutor executor) {
    this.jobs = jobs;
    this.launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(repository);
    launcher.setTaskExecutor(executor);
    try {
      launcher.afterPropertiesSet();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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
