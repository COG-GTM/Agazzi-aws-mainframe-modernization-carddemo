package com.carddemo.online.auth.service;

import com.carddemo.contracts.port.BusinessClock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * POPULATE-HEADER-INFO of COSGN00C/COMEN01C/COADM01C: titles from COTTL01Y, CURDATE {@code
 * MM/DD/YY} and CURTIME {@code HH:MM:SS} (CSDAT01Y) from the business clock.
 */
public record ScreenHeader(
    String trnname,
    String pgmname,
    String title01,
    String title02,
    String curdate,
    String curtime) {

  /** COTTL01Y CCDA-TITLE01 (PIC X(40)). */
  public static final String CCDA_TITLE01 = "      AWS Mainframe Modernization       ";

  /** COTTL01Y CCDA-TITLE02 (PIC X(40)). */
  public static final String CCDA_TITLE02 = "              CardDemo                  ";

  private static final DateTimeFormatter MM_DD_YY = DateTimeFormatter.ofPattern("MM/dd/yy");
  private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

  public static ScreenHeader of(String tranId, String program, BusinessClock clock) {
    LocalDateTime now = clock.now();
    return new ScreenHeader(
        tranId,
        program,
        ScreenText.out(CCDA_TITLE01),
        ScreenText.out(CCDA_TITLE02),
        MM_DD_YY.format(now),
        HH_MM_SS.format(now));
  }
}
