package com.carddemo.batch.reports.support;

/**
 * Raised where the COBOL program performs {@code 9999-ABEND-PROGRAM} ({@code CALL 'CEE3ABD'}).
 * Mapped by {@link AbendExitStatus} to step/job exit status {@code ABEND} and return code 16 (the
 * value produced by the golden harness CEE3ABD stub).
 */
public class JobAbendException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String program;

  public JobAbendException(String program, String message) {
    super(program + ": " + message);
    this.program = program;
  }

  public JobAbendException(String program, String message, Throwable cause) {
    super(program + ": " + message, cause);
    this.program = program;
  }

  public String program() {
    return program;
  }
}
