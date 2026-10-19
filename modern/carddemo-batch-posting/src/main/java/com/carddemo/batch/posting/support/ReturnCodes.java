package com.carddemo.batch.posting.support;

import java.util.Locale;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;

/**
 * Mapping between JCL step condition codes / abends and Spring Batch exit statuses.
 *
 * <ul>
 *   <li>RC=0 → {@code COMPLETED}
 *   <li>RC=n (n&gt;0) → {@code RC000n} (step and job {@code COMPLETED}, e.g. RC=4 with rejects)
 *   <li>abend U0999 / IDCAMS terminating error → {@code ABEND-U0999} / {@code ABEND-RC0012} with
 *       status {@code FAILED} (restartable from the failed step)
 *   <li>bypassed by {@code COND=} → {@code BYPASSED}
 * </ul>
 */
public final class ReturnCodes {

  /** Step execution-context key holding the step return code. */
  public static final String RC_KEY = "returnCode";

  public static final String BYPASSED = "BYPASSED";

  private ReturnCodes() {}

  public static ExitStatus exitStatus(int rc) {
    if (rc == 0) {
      return ExitStatus.COMPLETED;
    }
    return new ExitStatus(rcCode(rc), "RC=" + rc);
  }

  public static String rcCode(int rc) {
    return String.format(Locale.ROOT, "RC%04d", rc);
  }

  public static ExitStatus abend(CobolAbendException e) {
    String code =
        e.abendCode() == CobolAbendException.PROGRAM_ABEND
            ? String.format(Locale.ROOT, "ABEND-U%04d", e.abendCode())
            : "ABEND-" + rcCode(e.abendCode());
    return new ExitStatus(code, e.getMessage());
  }

  /** Process exit code for an exit status code (abend U0999 maps to 16, like CEE3ABD). */
  public static int processExitCode(String exitCode) {
    if (exitCode == null) {
      return 16;
    }
    if (exitCode.equals(ExitStatus.COMPLETED.getExitCode())
        || exitCode.equals(BYPASSED)
        || exitCode.equals(ExitStatus.NOOP.getExitCode())) {
      return 0;
    }
    if (exitCode.startsWith("RC")) {
      return Integer.parseInt(exitCode.substring(2));
    }
    if (exitCode.startsWith("ABEND-RC")) {
      return Integer.parseInt(exitCode.substring("ABEND-RC".length()));
    }
    return 16;
  }

  /** Highest return code of the steps already executed in the job (JCL MAXCC). */
  public static int maxPreviousRc(StepExecution current) {
    int max = 0;
    for (StepExecution s : current.getJobExecution().getStepExecutions()) {
      if (s.getId() != null && s.getId().equals(current.getId())) {
        continue;
      }
      if (s.getExecutionContext().containsKey(RC_KEY)) {
        max = Math.max(max, s.getExecutionContext().getInt(RC_KEY));
      }
    }
    return max;
  }
}
