package com.carddemo.batch.reports.support;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/**
 * Maps abends to exit statuses: a step failing with {@link JobAbendException} ends with exit code
 * {@code ABEND} (JCL RC 16, CEE3ABD), any other failure with {@code FAILED} (RC 12); successful
 * steps keep {@code COMPLETED} (RC 0). The job exit status mirrors the worst step.
 */
public final class AbendExitStatus implements StepExecutionListener, JobExecutionListener {

  public static final String ABEND = "ABEND";

  /** JCL-style return code of an exit status. */
  public static int returnCode(ExitStatus status) {
    return switch (status.getExitCode()) {
      case "COMPLETED", "NOOP" -> 0;
      case ABEND -> 16;
      default -> 12;
    };
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    for (Throwable t : stepExecution.getFailureExceptions()) {
      for (Throwable c = t; c != null; c = c.getCause()) {
        if (c instanceof JobAbendException abend) {
          return new ExitStatus(ABEND, abend.getMessage());
        }
      }
    }
    return stepExecution.getExitStatus();
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    for (StepExecution step : jobExecution.getStepExecutions()) {
      if (ABEND.equals(step.getExitStatus().getExitCode())) {
        jobExecution.setExitStatus(
            new ExitStatus(
                ABEND, step.getStepName() + " " + step.getExitStatus().getExitDescription()));
      }
    }
  }
}
