package com.carddemo.batch.posting.support;

import java.util.Set;
import org.springframework.batch.core.JobExecution;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.batch.JobExecutionEvent;
import org.springframework.context.ApplicationListener;

/**
 * Exposes the JCL condition code of a B1 job run from the CLI as the JVM exit code ({@code
 * SpringApplication.exit}): RC=4 for POSTTRAN with rejects, 16 for an abend.
 */
public class PostingExitCodeGenerator
    implements ExitCodeGenerator, ApplicationListener<JobExecutionEvent> {

  private final Set<String> jobNames;
  private volatile int exitCode;

  public PostingExitCodeGenerator(Set<String> jobNames) {
    this.jobNames = Set.copyOf(jobNames);
  }

  @Override
  public void onApplicationEvent(JobExecutionEvent event) {
    JobExecution execution = event.getJobExecution();
    if (jobNames.contains(execution.getJobInstance().getJobName())) {
      exitCode =
          Math.max(exitCode, ReturnCodes.processExitCode(execution.getExitStatus().getExitCode()));
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
