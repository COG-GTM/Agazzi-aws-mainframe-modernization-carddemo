package com.carddemo.ext.mqinquiry;

/**
 * {@code MqInquiryReply} of the OpenAPI contract.
 *
 * @param reply fixed-format reply text as sent on the MQ reply queue
 * @param correlationId correlation id of the inquiry
 */
public record MqInquiryReply(String reply, String correlationId) {}
