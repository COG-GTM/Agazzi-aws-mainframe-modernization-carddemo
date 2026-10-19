package com.carddemo.batch.orchestration.jobs;

import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.contracts.port.WaitService;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * WAITSTEP step WAIT (COBSWAIT): {@code ACCEPT PARM-VALUE FROM SYSIN}, MOVE to {@code PIC 9(8)
 * COMP}, {@code CALL 'MVSWAIT'}. SYSIN becomes the {@code centiseconds} JobParameter.
 */
public class WaitTasklet implements Tasklet {

  private final WaitService waitService;
  private final String defaultSysin;

  public WaitTasklet(WaitService waitService, String defaultSysin) {
    this.waitService = waitService;
    this.defaultSysin = defaultSysin;
  }

  /**
   * COBSWAIT {@code MOVE PARM-VALUE (X(8)) TO MVSWAIT-TIME (9(8) COMP)} as captured by the golden
   * masters (WAITSTEP, WAITSTEP-EMPTY, WAITSTEP-MALFORMED): first 8 bytes, blanks ignored, sign
   * dropped (unsigned target), any other non-digit → 0.
   */
  static long centiseconds(String sysin) {
    String field = sysin.length() > 8 ? sysin.substring(0, 8) : sysin;
    String digits = field.strip();
    if (!digits.isEmpty() && (digits.charAt(0) == '-' || digits.charAt(0) == '+')) {
      digits = digits.substring(1);
    }
    if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
      return 0;
    }
    return Long.parseLong(digits);
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunk) {
    Object sysin = chunk.getStepContext().getJobParameters().get("centiseconds");
    long cs = centiseconds(sysin == null ? defaultSysin : sysin.toString());
    chunk.getStepContext().getStepExecution().getExecutionContext().putLong("centiseconds", cs);
    try {
      waitService.awaitCentiseconds(cs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw JclStepFailure.abend("S222", "WAITSTEP cancelled", e);
    }
    return RepeatStatus.FINISHED;
  }
}
