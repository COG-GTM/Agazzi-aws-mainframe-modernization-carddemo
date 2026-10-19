package com.carddemo.batch.orchestration.launch;

import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.batch.orchestration.core.StepWork;
import java.util.Set;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;

/** INTRDRJ1 step STEP01 (IEBGENER SYSUT1=JCL(INTRDRJ2) → INTRDR): fire-and-forget submit. */
public class SubmitJobTasklet implements StepWork {

  private final AsyncJobSubmitter submitter;
  private final String jobName;

  public SubmitJobTasklet(AsyncJobSubmitter submitter, String jobName) {
    this.submitter = submitter;
    this.jobName = jobName;
  }

  @Override
  public void run(StepExecution step) throws Exception {
    String slot = step.getJobExecution().getJobInstance().getJobName() + "/" + step.getStepName();
    try {
      JobExecution submitted =
          submitter.submit(
              jobName, ChildJobParameters.of(step.getJobParameters(), slot, Set.of("in", "out")));
      step.getExecutionContext().putLong("submitted.executionId", submitted.getId());
    } catch (NoSuchJobException e) {
      throw JclStepFailure.rc(12, "INTRDR: member " + jobName + " not found");
    } catch (JobInstanceAlreadyCompleteException e) {
      step.getExecutionContext().putString("submitted.status", "ALREADY-COMPLETE");
    }
  }
}
