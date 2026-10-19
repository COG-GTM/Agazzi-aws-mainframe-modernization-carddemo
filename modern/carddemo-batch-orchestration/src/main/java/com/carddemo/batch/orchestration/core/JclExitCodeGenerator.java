package com.carddemo.batch.orchestration.core;

import org.springframework.batch.core.JobExecution;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.batch.JobExecutionEvent;
import org.springframework.context.ApplicationListener;

/** CLI exit code = highest JCL condition code of the jobs run by {@code spring.batch.job.name}. */
public class JclExitCodeGenerator
    implements ApplicationListener<JobExecutionEvent>, ExitCodeGenerator {

  private volatile int maxRc;

  @Override
  public void onApplicationEvent(JobExecutionEvent event) {
    record(event.getJobExecution());
  }

  public void record(JobExecution execution) {
    maxRc = Math.max(maxRc, JclReturnCodes.of(execution));
  }

  @Override
  public int getExitCode() {
    return maxRc;
  }
}
