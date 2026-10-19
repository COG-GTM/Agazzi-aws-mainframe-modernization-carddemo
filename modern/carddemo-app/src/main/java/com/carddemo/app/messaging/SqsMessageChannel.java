package com.carddemo.app.messaging;

import com.carddemo.contracts.port.MessageChannelPort;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/** MessageChannelPort over SQS (ADR-003): channel = queue name, headers = message attributes. */
public final class SqsMessageChannel implements MessageChannelPort {

  private final SqsClient sqs;
  private final Map<String, String> urls = new ConcurrentHashMap<>();

  public SqsMessageChannel(SqsClient sqs) {
    this.sqs = sqs;
  }

  private String url(String channel) {
    return urls.computeIfAbsent(channel, c -> sqs.createQueue(b -> b.queueName(c)).queueUrl());
  }

  @Override
  public String send(String channel, String payload, Map<String, String> headers) {
    Map<String, MessageAttributeValue> attrs = new HashMap<>();
    headers.forEach(
        (k, v) ->
            attrs.put(
                k, MessageAttributeValue.builder().dataType("String").stringValue(v).build()));
    return sqs.sendMessage(
            b -> b.queueUrl(url(channel)).messageBody(payload).messageAttributes(attrs))
        .messageId();
  }

  @Override
  public Optional<ChannelMessage> receive(String channel, Duration wait) {
    int seconds = (int) Math.min(20, Math.max(0, wait.toSeconds()));
    var resp =
        sqs.receiveMessage(
            b ->
                b.queueUrl(url(channel))
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(seconds)
                    .messageAttributeNames("All"));
    if (!resp.hasMessages() || resp.messages().isEmpty()) {
      return Optional.empty();
    }
    Message m = resp.messages().get(0);
    sqs.deleteMessage(b -> b.queueUrl(url(channel)).receiptHandle(m.receiptHandle()));
    Map<String, String> headers = new HashMap<>();
    m.messageAttributes().forEach((k, v) -> headers.put(k, v.stringValue()));
    return Optional.of(
        new ChannelMessage(
            m.messageId(),
            headers.getOrDefault("correlationId", m.messageId()),
            m.body(),
            headers));
  }
}
