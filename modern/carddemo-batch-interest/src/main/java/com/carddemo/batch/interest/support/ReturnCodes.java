package com.carddemo.batch.interest.support;

import org.springframework.batch.core.ExitStatus;

/** JCL condition codes ↔ Spring Batch {@link ExitStatus} ({@code COMPLETED} = RC 0, else RCnn). */
public final class ReturnCodes {

  public static final int OK = 0;
  public static final int IDCAMS_ERROR = 12;
  public static final int ABEND = 16;

  private static final String PREFIX = "RC";

  private ReturnCodes() {}

  public static ExitStatus exitStatus(int rc, String description) {
    return rc == OK
        ? ExitStatus.COMPLETED
        : new ExitStatus(PREFIX + "%02d".formatted(rc), description == null ? "" : description);
  }

  /** RC of an exit status: COMPLETED/NOOP → 0, RCnn → nn, any other failure → 16. */
  public static int of(ExitStatus status) {
    String code = status.getExitCode();
    if (ExitStatus.COMPLETED.getExitCode().equals(code)
        || ExitStatus.NOOP.getExitCode().equals(code)) {
      return OK;
    }
    if (code.startsWith(PREFIX)) {
      try {
        return Integer.parseInt(code.substring(PREFIX.length()));
      } catch (NumberFormatException e) {
        return ABEND;
      }
    }
    return ABEND;
  }

  public static int of(Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof Abend a) {
        return a.returnCode();
      }
    }
    return ABEND;
  }
}
