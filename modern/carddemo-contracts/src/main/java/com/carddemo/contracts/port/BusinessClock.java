package com.carddemo.contracts.port;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Business date/time source (plan §3.4 rule 5, §11.2). Never call {@code LocalDateTime.now()}
 * directly: tests and golden runs use a fixed clock identical to the GnuCOBOL harness.
 */
public interface BusinessClock {

  /** COBOL timestamp format {@code YYYY-MM-DD-HH.MM.SS.ffffff} (e.g. TRAN-PROC-TS). */
  DateTimeFormatter COBOL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS");

  Clock clock();

  default LocalDateTime now() {
    return LocalDateTime.now(clock());
  }

  default LocalDate today() {
    return LocalDate.now(clock());
  }

  /** {@link #now()} formatted as a 26-byte COBOL timestamp. */
  default String cobolTimestamp() {
    return COBOL_TIMESTAMP.format(now());
  }
}
