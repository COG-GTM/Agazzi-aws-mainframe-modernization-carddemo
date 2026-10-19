package com.carddemo.online.account.support;

import com.carddemo.contracts.port.BusinessClock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** {@code 1100-SCREEN-INIT}/{@code 3100-SCREEN-INIT}: titles (COTTL01Y), date and time. */
public record ScreenHeader(String title01, String title02, String curdate, String curtime) {

  /** CCDA-TITLE01. */
  public static final String TITLE01 = "      AWS Mainframe Modernization       ";

  /** CCDA-TITLE02. */
  public static final String TITLE02 = "              CardDemo                  ";

  private static final DateTimeFormatter MM_DD_YY = DateTimeFormatter.ofPattern("MM/dd/yy");
  private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

  public static ScreenHeader of(BusinessClock clock) {
    LocalDateTime now = clock.now();
    return new ScreenHeader(
        TITLE01.stripTrailing(),
        TITLE02.stripTrailing(),
        MM_DD_YY.format(now),
        HH_MM_SS.format(now));
  }
}
