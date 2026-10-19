package com.carddemo.ext.mqinquiry;

import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import java.util.Map;
import java.util.function.Function;

/**
 * 3000-GET-REQUEST / 4100-PUT-REPLY / 9000-ERROR over {@link MessageChannelPort}. Like the legacy
 * programs the reply goes to the fixed reply queue (MQMD-REPLYTOQ is saved but not used) and keeps
 * the request correlation id; requests longer than MQ-BUFFER-LENGTH fail like MQGET with
 * MQRC_TRUNCATED_MSG_FAILED. Payloads are mapped onto the single-byte repertoire first so the
 * 1000-character buffer is also 1000 bytes. Writes are retried a few times because the transport
 * may already have consumed the request.
 */
public final class MqInquiryProcessor {

  static final String CORRELATION_ID = "correlationId";
  static final String REQUEST_ID = "requestId";
  static final long MQCC_WARNING = 1;
  static final long MQRC_TRUNCATED_MSG_FAILED = 2080;
  static final String REPLY_TO = "replyTo";
  static final int SEND_ATTEMPTS = 3;

  private final MessageChannelPort channel;
  private final String errorQueue;
  private final boolean honorReplyTo;

  public MqInquiryProcessor(MessageChannelPort channel, String errorQueue) {
    this(channel, errorQueue, false);
  }

  public MqInquiryProcessor(MessageChannelPort channel, String errorQueue, boolean honorReplyTo) {
    this.channel = channel;
    this.errorQueue = errorQueue;
    this.honorReplyTo = honorReplyTo;
  }

  /** Processes one request and returns the id of the message written (reply or error). */
  public String handle(
      ChannelMessage request,
      String inputQueue,
      String replyQueue,
      Function<String, InquiryOutcome> program) {
    String payload = MqMessageLayout.ascii(request.payload());
    if (payload.length() > MqMessageLayout.MESSAGE_LENGTH) {
      return send(
          errorQueue,
          MqMessageLayout.buffer(
              MqErrorMessage.format(
                  "", "INP MQGET ERR:", MQCC_WARNING, MQRC_TRUNCATED_MSG_FAILED, inputQueue)),
          Map.of());
    }
    InquiryOutcome outcome = program.apply(payload);
    if (!outcome.reply()) {
      return send(errorQueue, outcome.payload(), Map.of());
    }
    String correlationId = request.correlationId() == null ? request.id() : request.correlationId();
    return send(
        replyQueue(request, replyQueue),
        outcome.payload(),
        Map.of(CORRELATION_ID, correlationId, REQUEST_ID, String.valueOf(request.id())));
  }

  private String replyQueue(ChannelMessage request, String fixedReplyQueue) {
    String replyTo = request.headers() == null ? null : request.headers().get(REPLY_TO);
    return honorReplyTo && replyTo != null && !replyTo.isBlank() ? replyTo : fixedReplyQueue;
  }

  private String send(String queue, String payload, Map<String, String> headers) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= SEND_ATTEMPTS; attempt++) {
      try {
        return channel.send(queue, payload, headers);
      } catch (RuntimeException e) {
        last = e;
        backoff(attempt);
      }
    }
    throw last;
  }

  private static void backoff(int attempt) {
    if (attempt == SEND_ATTEMPTS) {
      return;
    }
    try {
      Thread.sleep(100L * attempt);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
