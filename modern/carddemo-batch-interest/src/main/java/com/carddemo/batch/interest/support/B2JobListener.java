package com.carddemo.batch.interest.support;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Job RC = highest step RC (JES MAXCC). Also the process exit code of a CLI run ({@code
 * --spring.batch.job.name=INTCALC}), so schedulers see 0/12/16 like the mainframe job.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class B2JobListener implements JobExecutionListener, ExitCodeGenerator {

  private final AtomicInteger lastReturnCode = new AtomicInteger();

  @Override
  public void afterJob(JobExecution jobExecution) {
    int rc = 0;
    for (StepExecution step : jobExecution.getStepExecutions()) {
      rc = Math.max(rc, ReturnCodes.of(step.getExitStatus()));
    }
    if (jobExecution.getStatus() == BatchStatus.FAILED && rc == 0) {
      rc =
          jobExecution.getAllFailureExceptions().stream()
              .mapToInt(ReturnCodes::of)
              .max()
              .orElse(ReturnCodes.ABEND);
    }
    if (rc != 0) {
      jobExecution.setExitStatus(
          ReturnCodes.exitStatus(rc, jobExecution.getExitStatus().getExitDescription()));
    }
    lastReturnCode.set(rc);
  }

  public int lastReturnCode() {
    return lastReturnCode.get();
  }

  @Override
  public int getExitCode() {
    return lastReturnCode.get();
  }
}
