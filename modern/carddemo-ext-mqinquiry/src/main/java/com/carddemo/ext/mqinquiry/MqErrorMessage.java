package com.carddemo.ext.mqinquiry;

import static com.carddemo.ext.mqinquiry.MqMessageLayout.pic;
import static com.carddemo.ext.mqinquiry.MqMessageLayout.unsigned;

/** MQ-ERR-DISPLAY of CODATE01/COACCT01 (X25, 2sp, X25, 2sp, 9(2), 2sp, 9(5), 2sp, X48). */
public final class MqErrorMessage {

  private MqErrorMessage() {}

  public static String format(
      String paragraph, String returnMessage, long conditionCode, long reasonCode, String queue) {
    return pic(paragraph, 25)
        + "  "
        + pic(returnMessage, 25)
        + "  "
        + unsigned(conditionCode, 2)
        + "  "
        + unsigned(reasonCode, 5)
        + "  "
        + pic(queue, 48);
  }
}
