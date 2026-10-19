package com.carddemo.platform.wait;

import com.carddemo.contracts.port.WaitService;
import java.util.Objects;

/**
 * Port of {@code COBSWAIT}: {@code ACCEPT PARM-VALUE FROM SYSIN} ({@code PIC X(8)}), {@code MOVE
 * PARM-VALUE TO MVSWAIT-TIME} ({@code PIC 9(8) COMP}) and {@code CALL 'MVSWAIT'}. The
 * alphanumeric-to-numeric move takes the first 8 bytes as an unsigned integer whose digits are the
 * low nibbles of the characters, so a space counts as {@code 0}: {@code 00003600} waits 36 seconds,
 * {@code 500} (padded with spaces) waits 50 000 000 centiseconds.
 */
public final class CobsWait {

  private final WaitService waitService;

  public CobsWait(WaitService waitService) {
    this.waitService = Objects.requireNonNull(waitService, "waitService");
  }

  /** Runs the step with the given {@code SYSIN} card; returns the centiseconds waited. */
  public long run(String sysinCard) throws InterruptedException {
    long centiseconds = parmToCentiseconds(sysinCard);
    waitService.awaitCentiseconds(centiseconds);
    return centiseconds;
  }

  /** {@code MOVE PARM-VALUE TO MVSWAIT-TIME}. */
  public static long parmToCentiseconds(String sysinCard) {
    String parm = sysinCard == null ? "" : sysinCard;
    long value = 0;
    for (int i = 0; i < 8; i++) {
      char c = i < parm.length() ? parm.charAt(i) : ' ';
      int digit;
      if (c >= '0' && c <= '9') {
        digit = c - '0';
      } else if (c == ' ' || c == '\0') {
        digit = 0;
      } else {
        throw new IllegalArgumentException("non-numeric PARM-VALUE: '" + parm + "'");
      }
      value = value * 10 + digit;
    }
    return value;
  }
}
