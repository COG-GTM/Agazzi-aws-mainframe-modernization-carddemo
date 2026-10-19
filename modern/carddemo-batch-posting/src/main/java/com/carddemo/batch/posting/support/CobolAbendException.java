package com.carddemo.batch.posting.support;

/**
 * Raised where the COBOL program performs {@code 9999-ABEND-PROGRAM} (CEE3ABD) or an IDCAMS
 * function ends with a terminating return code.
 */
public class CobolAbendException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Abend code passed to CEE3ABD by CBTRN01C/CBTRN02C. */
  public static final int PROGRAM_ABEND = 999;

  private final int abendCode;
  private final String fileStatus;

  public CobolAbendException(int abendCode, String message, String fileStatus) {
    super(message);
    this.abendCode = abendCode;
    this.fileStatus = fileStatus;
  }

  public CobolAbendException(String message, String fileStatus) {
    this(PROGRAM_ABEND, message, fileStatus);
  }

  public CobolAbendException(String message, String fileStatus, Throwable cause) {
    super(message, cause);
    this.abendCode = PROGRAM_ABEND;
    this.fileStatus = fileStatus;
  }

  public int abendCode() {
    return abendCode;
  }

  /** Two-character VSAM/QSAM file status, or {@code null} when not file related. */
  public String fileStatus() {
    return fileStatus;
  }

  /** {@code FILE STATUS IS: NNNN0035} line written by 9910-DISPLAY-IO-STATUS. */
  public String fileStatusLine() {
    return fileStatus == null ? null : "FILE STATUS IS: NNNN00" + fileStatus;
  }
}
