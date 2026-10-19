package com.carddemo.ext.mqinquiry;

import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import java.util.Map;
import java.util.function.Function;

/**
 * 3000-GET-REQUEST / 4100-PUT-REPLY / 9000-ERROR over {@link MessageChannelPort}. Like the legacy
 * programs the reply goes to the fixed reply queue (MQMD-REPLYTOQ is saved but not used) and keeps
 * the request correlation id; requests longer than MQ-BUFFER-LENGTH fail like MQGET with
 * MQRC_TRUNCATED_MSG_FAILED.
 */
public final class MqInquiryProcessor {

  static final String CORRELATION_ID = "correlationId";
  static final String REQUEST_ID = "requestId";
  static final long MQCC_WARNING = 1;
  static final long MQRC_TRUNCATED_MSG_FAILED = 2080;

  private final MessageChannelPort channel;
  private final String errorQueue;

  public MqInquiryProcessor(MessageChannelPort channel, String errorQueue) {
    this.channel = channel;
    this.errorQueue = errorQueue;
  }

  /** Processes one request and returns the id of the message written (reply or error). */
  public String handle(
      ChannelMessage request,
      String inputQueue,
      String replyQueue,
      Function<String, InquiryOutcome> program) {
    String payload = request.payload() == null ? "" : request.payload();
    if (payload.length() > MqMessageLayout.MESSAGE_LENGTH) {
      return channel.send(
          errorQueue,
          MqMessageLayout.buffer(
              MqErrorMessage.format(
                  "", "INP MQGET ERR:", MQCC_WARNING, MQRC_TRUNCATED_MSG_FAILED, inputQueue)),
          Map.of());
    }
    InquiryOutcome outcome = program.apply(payload);
    if (!outcome.reply()) {
      return channel.send(errorQueue, outcome.payload(), Map.of());
    }
    String correlationId = request.correlationId() == null ? request.id() : request.correlationId();
    return channel.send(
        replyQueue,
        outcome.payload(),
        Map.of(CORRELATION_ID, correlationId, REQUEST_ID, String.valueOf(request.id())));
  }
}
