package com.carddemo.online.useradmin.support;

import com.carddemo.contracts.port.BusinessClock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** POPULATE-HEADER-INFO: COTTL01Y titles plus CURDATE (MM/DD/YY) and CURTIME (HH:MM:SS). */
public record ScreenHeader(
    String trnname,
    String title01,
    String curdate,
    String pgmname,
    String title02,
    String curtime) {

  /** CCDA-TITLE01 of COTTL01Y. */
  public static final String TITLE01 = "      AWS Mainframe Modernization       ";

  /** CCDA-TITLE02 of COTTL01Y. */
  public static final String TITLE02 = "              CardDemo                  ";

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yy");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  public static ScreenHeader of(String tranId, String program, BusinessClock clock) {
    LocalDateTime now = clock.now();
    return new ScreenHeader(tranId, TITLE01, DATE.format(now), program, TITLE02, TIME.format(now));
  }
}
