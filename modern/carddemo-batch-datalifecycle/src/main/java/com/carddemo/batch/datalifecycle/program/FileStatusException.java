package com.carddemo.batch.datalifecycle.program;

import java.io.IOException;

/** An I/O failure carrying the VSAM/QSAM file status the legacy program would have seen. */
public final class FileStatusException extends IOException {

  private static final long serialVersionUID = 1L;

  /** Open failure (file not found). */
  public static final String NOT_FOUND = "35";

  /** Record length does not match the file definition. */
  public static final String WRONG_LENGTH = "04";

  /** Permanent I/O error. */
  public static final String IO_ERROR = "30";

  private final String status;

  public FileStatusException(String status, String message) {
    super(message);
    this.status = status;
  }

  public FileStatusException(String status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public String status() {
    return status;
  }
}
