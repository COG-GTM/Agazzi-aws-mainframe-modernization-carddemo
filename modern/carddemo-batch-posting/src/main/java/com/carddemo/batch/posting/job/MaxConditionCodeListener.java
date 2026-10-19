package com.carddemo.batch.posting.job;

import com.carddemo.batch.posting.support.ReturnCodes;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;

/**
 * JCL job condition code: steps without {@code COND=} run even after a nonzero RC, and the job ends
 * with the highest step RC (e.g. TRANIDX STEP20 RC=12 is not masked by STEP30 RC=0).
 */
class MaxConditionCodeListener implements JobExecutionListener {

  @Override
  public void afterJob(JobExecution jobExecution) {
    if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
      return;
    }
    int max = 0;
    for (StepExecution s : jobExecution.getStepExecutions()) {
      if (s.getExecutionContext().containsKey(ReturnCodes.RC_KEY)) {
        max = Math.max(max, s.getExecutionContext().getInt(ReturnCodes.RC_KEY));
      }
    }
    if (max > 0) {
      ExitStatus status = ReturnCodes.exitStatus(max);
      jobExecution.setExitStatus(new ExitStatus(status.getExitCode(), "MAXCC=" + max));
    }
  }
}
