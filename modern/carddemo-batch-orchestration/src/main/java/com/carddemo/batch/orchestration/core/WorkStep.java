package com.carddemo.batch.orchestration.core;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.step.AbstractStep;

/**
 * Non-transactional step (like Spring Batch's {@code JobStep}): the JobRepository refuses to create
 * a job execution inside an active transaction, which a tasklet step always has.
 */
public class WorkStep extends AbstractStep {

  private final StepWork work;

  public WorkStep(String name, StepWork work) {
    super(name);
    this.work = work;
  }

  @Override
  protected void doExecute(StepExecution stepExecution) throws Exception {
    work.run(stepExecution);
  }
}
