package com.carddemo.batch.orchestration.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;

/**
 * ExitStatus → JCL condition code (what Control-M/CA-7 test with COND/OUTCOND).
 *
 * <ul>
 *   <li>{@code COMPLETED}, {@code NOOP}, {@code NOT-ORDERED} → 0
 *   <li>{@code RCnn} → nn (e.g. IDCAMS RC 12)
 *   <li>{@code STOPPED} → 8 (operator stop, restartable)
 *   <li>{@code FAILED} (unclassified exception) → 12
 *   <li>{@code ABEND-*}, {@code NOJOB} (JCL error: job not found), {@code UNKNOWN} → 16
 * </ul>
 */
public final class JclReturnCodes {

  public static final String NOJOB = "NOJOB";
  public static final String NOT_ORDERED = "NOT-ORDERED";
  public static final int RC_STOPPED = 8;
  public static final int RC_FAILED = 12;
  public static final int RC_ABEND = 16;

  private static final Pattern RC = Pattern.compile("RC(\\d{1,4})");

  private JclReturnCodes() {}

  public static String rcExitCode(int rc) {
    return "RC%02d".formatted(rc);
  }

  public static int of(ExitStatus status) {
    String code = status.getExitCode();
    Matcher m = RC.matcher(code);
    if (m.matches()) {
      return Integer.parseInt(m.group(1));
    }
    if (code.startsWith("ABEND") || NOJOB.equals(code)) {
      return RC_ABEND;
    }
    return switch (code) {
      case "COMPLETED", "NOOP", NOT_ORDERED -> 0;
      case "STOPPED" -> RC_STOPPED;
      case "FAILED" -> RC_FAILED;
      default -> RC_ABEND;
    };
  }

  /** Highest step RC of a job (JCL MAXCC), never lower than what the job status implies. */
  public static int of(JobExecution execution) {
    int max =
        execution.getStepExecutions().stream().mapToInt(s -> of(s.getExitStatus())).max().orElse(0);
    int job = of(execution.getExitStatus());
    if (execution.getStatus() == BatchStatus.COMPLETED && job == 0) {
      return max;
    }
    if (!"FAILED".equals(execution.getExitStatus().getExitCode()) && job != 0) {
      return Math.max(max, job);
    }
    // Generic FAILED: the RC of the step that failed explains it; otherwise at least RC 12.
    int failing =
        execution.getStepExecutions().stream()
            .filter(s -> s.getStatus().isUnsuccessful())
            .mapToInt(s -> of(s.getExitStatus()))
            .max()
            .orElse(0);
    return Math.max(max, failing > 0 ? failing : RC_FAILED);
  }
}
