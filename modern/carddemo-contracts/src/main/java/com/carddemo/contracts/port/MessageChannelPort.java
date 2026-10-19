package com.carddemo.contracts.port;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Message queue abstraction replacing MQ MQPUT/MQGET (X1, X3). Production adapter: SQS (ADR-003);
 * tests: in-memory or LocalStack. Payloads keep the legacy fixed-format text.
 */
public interface MessageChannelPort {

  record ChannelMessage(
      String id, String correlationId, String payload, Map<String, String> headers) {}

  /** MQPUT. Returns the message id. */
  String send(String channel, String payload, Map<String, String> headers);

  /** MQGET with wait interval. */
  Optional<ChannelMessage> receive(String channel, Duration wait);

  /**
   * Like {@link #receive(String, Duration)}, but the message stays in flight until {@link
   * #acknowledge(String, ChannelMessage)} (SQS: deleted on ack, redelivered after the visibility
   * timeout). Default: auto-acknowledging {@code receive} (contracts-v1.1).
   */
  default Optional<ChannelMessage> receiveUnacknowledged(String channel, Duration wait) {
    return receive(channel, wait);
  }

  /** Confirms a message from {@link #receiveUnacknowledged}; no-op for auto-ack adapters. */
  default void acknowledge(String channel, ChannelMessage message) {}

  /** Request/reply (MQPUT on request queue + MQGET by correlation id on the reply queue). */
  default Optional<ChannelMessage> request(
      String requestChannel, String replyChannel, String payload, Duration timeout) {
    String id = send(requestChannel, payload, Map.of("replyTo", replyChannel));
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      Optional<ChannelMessage> m =
          receive(replyChannel, Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
      if (m.isPresent() && id.equals(m.get().correlationId())) {
        return m;
      }
    }
    return Optional.empty();
  }
}
