package com.carddemo.batch.orchestration.core;

/**
 * A step ended the way a JCL step ends badly: {@code RCnn} (program/utility return code) or {@code
 * ABEND-Sxxx}/{@code ABEND-Uxxxx}. The exit code becomes the step {@code ExitStatus}.
 */
public class JclStepFailure extends RuntimeException {

  private final String exitCode;

  public JclStepFailure(String exitCode, String message) {
    this(exitCode, message, null);
  }

  public JclStepFailure(String exitCode, String message, Throwable cause) {
    super(message, cause);
    this.exitCode = exitCode;
  }

  public static JclStepFailure rc(int rc, String message) {
    return new JclStepFailure(JclReturnCodes.rcExitCode(rc), message);
  }

  public static JclStepFailure abend(String code, String message, Throwable cause) {
    return new JclStepFailure("ABEND-" + code, message, cause);
  }

  public String exitCode() {
    return exitCode;
  }
}
