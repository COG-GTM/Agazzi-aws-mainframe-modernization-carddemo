package com.carddemo.batch.posting.job;

import com.carddemo.batch.posting.support.CobolAbendException;
import com.carddemo.batch.posting.support.ReturnCodes;
import com.carddemo.batch.posting.support.Sysout;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * One JCL step ({@code EXEC PGM=}): runs the program, records its return code in the step context
 * ({@link ReturnCodes#RC_KEY}) and maps it / an abend to the step exit status.
 */
abstract class JclStepTasklet implements Tasklet, StepExecutionListener {

  private final String program;

  JclStepTasklet(String program) {
    this.program = program;
  }

  /** Runs the step and returns its condition code. */
  protected abstract int run(PostingJobParameters params, Sysout sysout, StepExecution step);

  /** JCL {@code COND=}: true when the step must be bypassed. */
  protected boolean bypass(StepExecution step) {
    return false;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    StepExecution step = chunkContext.getStepContext().getStepExecution();
    PostingJobParameters params = PostingJobParameters.from(step.getJobParameters());
    Sysout sysout = new Sysout(program);
    if (bypass(step)) {
      contribution.setExitStatus(new ExitStatus(ReturnCodes.BYPASSED, "COND= bypass"));
      return RepeatStatus.FINISHED;
    }
    try {
      int rc = run(params, sysout, step);
      step.getExecutionContext().putInt(ReturnCodes.RC_KEY, rc);
      contribution.setExitStatus(ReturnCodes.exitStatus(rc));
      sysout.appendTo(params.sysout(), step.getStepName(), rc);
      return RepeatStatus.FINISHED;
    } catch (CobolAbendException e) {
      step.getExecutionContext().putInt(ReturnCodes.RC_KEY, ReturnCodes.processExitCode(code(e)));
      sysout.appendTo(params.sysout(), step.getStepName(), ReturnCodes.processExitCode(code(e)));
      throw e;
    }
  }

  private static String code(CobolAbendException e) {
    return ReturnCodes.abend(e).getExitCode();
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    for (Throwable t : stepExecution.getFailureExceptions()) {
      Throwable c = t;
      while (c != null) {
        if (c instanceof CobolAbendException abend) {
          return ReturnCodes.abend(abend);
        }
        c = c.getCause();
      }
    }
    return null;
  }
}
