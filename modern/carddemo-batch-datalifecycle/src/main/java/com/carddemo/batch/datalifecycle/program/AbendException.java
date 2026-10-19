package com.carddemo.batch.datalifecycle.program;

/** CEE3ABD: the program ended abnormally; the step return code is 16. */
public final class AbendException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static final int RETURN_CODE = 16;

  private final int abendCode;

  public AbendException(String program, int abendCode, Throwable cause) {
    super(program + " ABEND U" + abendCode, cause);
    this.abendCode = abendCode;
  }

  public int abendCode() {
    return abendCode;
  }
}
