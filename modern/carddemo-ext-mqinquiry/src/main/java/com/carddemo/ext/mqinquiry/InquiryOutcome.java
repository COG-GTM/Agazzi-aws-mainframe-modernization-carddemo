package com.carddemo.ext.mqinquiry;

/**
 * Result of processing one request: either a reply for the reply queue or an MQ-ERR-DISPLAY message
 * for the error queue (legacy 9000-ERROR, after which no reply is sent).
 *
 * @param reply true for a reply, false for an error-queue message
 * @param payload 1000-byte message body
 */
public record InquiryOutcome(boolean reply, String payload) {

  public static InquiryOutcome reply(String payload) {
    return new InquiryOutcome(true, MqMessageLayout.buffer(payload));
  }

  public static InquiryOutcome error(String payload) {
    return new InquiryOutcome(false, MqMessageLayout.buffer(payload));
  }
}
