package com.carddemo.batch.datalifecycle.job;

/**
 * A step that ends with a failing condition code (IDCAMS/utility RC 8/12). The step fails so the
 * job stops and can be restarted from it; the RC becomes the step exit code {@code RC=<n>}.
 */
public class ConditionCodeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int rc;

  public ConditionCodeException(int rc, String message) {
    super(message);
    this.rc = rc;
  }

  public ConditionCodeException(int rc, String message, Throwable cause) {
    super(message, cause);
    this.rc = rc;
  }

  public int rc() {
    return rc;
  }
}
