package com.carddemo.batch.interest.support;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

/** Maps a failed step to its JCL return code ({@code RC16} abend, {@code RC12} IDCAMS…). */
@Component
public class B2StepListener implements StepExecutionListener {

  private final Sysout sysout;

  public B2StepListener(Sysout sysout) {
    this.sysout = sysout;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    sysout.reset();
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    sysout.flush(stepExecution.getJobParameters().getString(Sysout.PARAMETER));
    if (stepExecution.getStatus() != BatchStatus.FAILED) {
      return stepExecution.getExitStatus();
    }
    Throwable failure =
        stepExecution.getFailureExceptions().isEmpty()
            ? null
            : stepExecution.getFailureExceptions().get(0);
    int rc = failure == null ? ReturnCodes.ABEND : ReturnCodes.of(failure);
    return ReturnCodes.exitStatus(rc, failure == null ? "" : String.valueOf(failure.getMessage()));
  }
}
