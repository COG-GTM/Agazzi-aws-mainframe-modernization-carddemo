package com.carddemo.ext.mqinquiry;

import com.carddemo.contracts.port.BusinessClock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CODATE01 4000-PROCESS-REQUEST-REPLY: ASKTIME + FORMATTIME MMDDYYYY DATESEP('-') TIME TIMESEP,
 * reply {@code 'SYSTEM DATE : ' WS-MMDDYYYY 'SYSTEM TIME : ' WS-TIME}. The request body is ignored.
 */
public final class SystemDateInquiry {

  private static final DateTimeFormatter MMDDYYYY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final BusinessClock clock;

  public SystemDateInquiry(BusinessClock clock) {
    this.clock = clock;
  }

  public InquiryOutcome process(String request) {
    LocalDateTime now = clock.now();
    return InquiryOutcome.reply(
        "SYSTEM DATE : " + MMDDYYYY.format(now) + "SYSTEM TIME : " + TIME.format(now));
  }
}
