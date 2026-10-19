package com.carddemo.platform.messaging;

import com.carddemo.contracts.port.MessageChannelPort;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** In-memory queues for tests and the default profile (SQS adapter: carddemo-app, ADR-003). */
public final class InMemoryMessageChannel implements MessageChannelPort {

  private final Map<String, BlockingQueue<ChannelMessage>> queues = new ConcurrentHashMap<>();

  private BlockingQueue<ChannelMessage> queue(String channel) {
    return queues.computeIfAbsent(channel, c -> new LinkedBlockingQueue<>());
  }

  @Override
  public String send(String channel, String payload, Map<String, String> headers) {
    String id = UUID.randomUUID().toString();
    String correlation = headers.getOrDefault("correlationId", id);
    queue(channel).add(new ChannelMessage(id, correlation, payload, Map.copyOf(headers)));
    return id;
  }

  @Override
  public Optional<ChannelMessage> receive(String channel, Duration wait) {
    try {
      return Optional.ofNullable(queue(channel).poll(wait.toMillis(), TimeUnit.MILLISECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
