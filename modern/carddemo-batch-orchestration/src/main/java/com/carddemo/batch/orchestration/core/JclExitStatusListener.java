package com.carddemo.batch.orchestration.core;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/** Turns a {@link JclStepFailure} into the step ExitStatus ({@code RC12}, {@code ABEND-U0001}). */
public class JclExitStatusListener implements StepExecutionListener {

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    return stepExecution.getFailureExceptions().stream()
        .map(JclExitStatusListener::jclFailure)
        .filter(f -> f != null)
        .findFirst()
        .map(f -> new ExitStatus(f.exitCode(), f.getMessage()))
        .orElse(stepExecution.getExitStatus());
  }

  private static JclStepFailure jclFailure(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c instanceof JclStepFailure f) {
        return f;
      }
    }
    return null;
  }
}
