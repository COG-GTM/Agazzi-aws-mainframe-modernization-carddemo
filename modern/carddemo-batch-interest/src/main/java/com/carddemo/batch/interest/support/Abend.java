package com.carddemo.batch.interest.support;

/** Legacy abnormal end: {@code CALL 'CEE3ABD'} (user abend) or a utility failure with its RC. */
public class Abend extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int returnCode;
  private final int abendCode;

  public Abend(int returnCode, int abendCode, String message) {
    super(message);
    this.returnCode = returnCode;
    this.abendCode = abendCode;
  }

  public Abend(int returnCode, String message, Throwable cause) {
    super(message, cause);
    this.returnCode = returnCode;
    this.abendCode = 0;
  }

  /** CEE3ABD with ABCODE 999 as issued by 9999-ABEND-PROGRAM (step ends with RC 16). */
  public static Abend user999(String message) {
    return new Abend(ReturnCodes.ABEND, 999, message);
  }

  public int returnCode() {
    return returnCode;
  }

  public int abendCode() {
    return abendCode;
  }
}
