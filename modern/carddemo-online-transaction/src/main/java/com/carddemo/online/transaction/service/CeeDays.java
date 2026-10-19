package com.carddemo.online.transaction.service;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * CSUTLDTC (wrapper of LE CEEDAYS with mask {@code YYYY-MM-DD}) as used by COTRN02C and CORPT00C:
 * the caller accepts severity 0 and message 2513 (CEE2513, date outside the Lilian range, i.e.
 * before 1582-10-15), everything else is "Not a valid date".
 */
public final class CeeDays {

  static final LocalDate LILIAN_START = LocalDate.of(1582, 10, 15);

  private CeeDays() {}

  /** Feedback of CEEDAYS: {@code 0} = valid, {@code 2513} = unsupported range, else an error. */
  public static int feedback(String yyyyMmDd) {
    if (yyyyMmDd == null || !yyyyMmDd.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return 2518;
    }
    int y = Integer.parseInt(yyyyMmDd.substring(0, 4));
    int m = Integer.parseInt(yyyyMmDd.substring(5, 7));
    int d = Integer.parseInt(yyyyMmDd.substring(8, 10));
    if (m < 1 || m > 12) {
      return 2517;
    }
    try {
      LocalDate date = LocalDate.of(y, m, d);
      return date.isBefore(LILIAN_START) ? 2513 : 0;
    } catch (DateTimeException e) {
      return 2508;
    }
  }

  /** {@code CSUTLDTC-RESULT-SEV-CD = '0000' OR CSUTLDTC-RESULT-MSG-NUM = '2513'}. */
  public static boolean accepted(String yyyyMmDd) {
    int fb = feedback(yyyyMmDd);
    return fb == 0 || fb == 2513;
  }
}
