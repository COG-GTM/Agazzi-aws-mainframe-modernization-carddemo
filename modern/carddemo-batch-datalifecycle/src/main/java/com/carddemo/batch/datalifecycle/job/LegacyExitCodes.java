package com.carddemo.batch.datalifecycle.job;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

/** Maps a job execution to the JCL condition code a scheduler would see (MAXCC). */
public final class LegacyExitCodes {

  private LegacyExitCodes() {}

  public static int of(JobExecution execution) {
    int max = execution.getExecutionContext().getInt(JclJobBuilder.MAXCC, 0);
    for (StepExecution s : execution.getStepExecutions()) {
      String code = s.getExitStatus().getExitCode();
      if (JclJobBuilder.ABEND.equals(code)) {
        max = Math.max(max, 16);
      } else if (code.startsWith("RC=")) {
        max = Math.max(max, Integer.parseInt(code.substring(3)));
      }
    }
    if (execution.getStatus() == BatchStatus.FAILED && max == 0) {
      max = 12;
    }
    return max;
  }
}
