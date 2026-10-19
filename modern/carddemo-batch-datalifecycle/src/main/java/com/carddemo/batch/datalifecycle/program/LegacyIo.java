package com.carddemo.batch.datalifecycle.program;

/** Shared I/O error reporting of the CBACT0xC / CBCUS01C family (9910-DISPLAY-IO-STATUS). */
final class LegacyIo {

  private LegacyIo() {}

  static String fileStatusLine(String status) {
    return "FILE STATUS IS: NNNN00" + status;
  }

  /** 9999-ABEND-PROGRAM with ABCODE 999. */
  static AbendException abend(
      Sysout sysout, String program, String message, FileStatusException e) {
    sysout.display(message);
    sysout.display(fileStatusLine(e.status()));
    sysout.display("ABENDING PROGRAM");
    sysout.display("CEE3ABD ABEND U999");
    return new AbendException(program, 999, e);
  }
}
