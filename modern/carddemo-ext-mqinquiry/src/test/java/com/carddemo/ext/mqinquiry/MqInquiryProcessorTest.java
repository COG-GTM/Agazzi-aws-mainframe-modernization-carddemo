package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import com.carddemo.platform.messaging.InMemoryMessageChannel;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MqInquiryProcessorTest {

  private final InMemoryMessageChannel channel = new InMemoryMessageChannel();
  private final MqInquiryProcessor processor = new MqInquiryProcessor(channel, "ERR");

  @Test
  void replyKeepsCorrelationIdAndUsesFixedReplyQueue() {
    processor.handle(
        new ChannelMessage("m1", "c1", "x", Map.of("replyTo", "ELSEWHERE")),
        "IN",
        "REPLY",
        p -> InquiryOutcome.reply("OK"));
    ChannelMessage reply = channel.receive("REPLY", Duration.ZERO).orElseThrow();
    assertThat(reply.correlationId()).isEqualTo("c1");
    assertThat(reply.payload()).hasSize(1000).startsWith("OK ");
    assertThat(channel.receive("ELSEWHERE", Duration.ZERO)).isEmpty();
  }

  @Test
  void missingCorrelationIdFallsBackToMessageId() {
    processor.handle(
        new ChannelMessage("m2", null, null, Map.of()), "IN", "REPLY", InquiryOutcome::reply);
    assertThat(channel.receive("REPLY", Duration.ZERO).orElseThrow().correlationId())
        .isEqualTo("m2");
  }

  @Test
  void errorOutcomeGoesToErrorQueueWithoutReply() {
    processor.handle(
        new ChannelMessage("m3", "c3", "x", Map.of()),
        "IN",
        "REPLY",
        p -> InquiryOutcome.error("BOOM"));
    assertThat(channel.receive("ERR", Duration.ZERO).orElseThrow().payload()).startsWith("BOOM");
    assertThat(channel.receive("REPLY", Duration.ZERO)).isEmpty();
  }

  @Test
  void replyToIsHonouredOnlyWhenEnabled() {
    new MqInquiryProcessor(channel, "ERR", true)
        .handle(
            new ChannelMessage("m5", "c5", "x", Map.of("replyTo", "PRIVATE")),
            "IN",
            "REPLY",
            InquiryOutcome::reply);
    assertThat(channel.receive("PRIVATE", Duration.ZERO)).isPresent();
    new MqInquiryProcessor(channel, "ERR", true)
        .handle(
            new ChannelMessage("m6", "c6", "x", Map.of()), "IN", "REPLY", InquiryOutcome::reply);
    assertThat(channel.receive("REPLY", Duration.ZERO)).isPresent();
  }

  @Test
  void transientSendFailureIsRetried() {
    AtomicInteger failures = new AtomicInteger(1);
    MessageChannelPort flaky =
        new MessageChannelPort() {
          @Override
          public String send(String ch, String payload, Map<String, String> headers) {
            if (failures.getAndDecrement() > 0) {
              throw new IllegalStateException("throttled");
            }
            return channel.send(ch, payload, headers);
          }

          @Override
          public Optional<ChannelMessage> receive(String ch, Duration wait) {
            return channel.receive(ch, wait);
          }
        };
    new MqInquiryProcessor(flaky, "ERR")
        .handle(
            new ChannelMessage("m7", "c7", "x", Map.of()), "IN", "REPLY", InquiryOutcome::reply);
    assertThat(channel.receive("REPLY", Duration.ZERO)).isPresent();
  }

  @Test
  void persistentSendFailureIsRethrown() {
    MessageChannelPort down =
        new MessageChannelPort() {
          @Override
          public String send(String ch, String payload, Map<String, String> headers) {
            throw new IllegalStateException("down");
          }

          @Override
          public Optional<ChannelMessage> receive(String ch, Duration wait) {
            return Optional.empty();
          }
        };
    assertThatThrownBy(
            () ->
                new MqInquiryProcessor(down, "ERR")
                    .handle(
                        new ChannelMessage("m8", "c8", "x", Map.of()),
                        "IN",
                        "REPLY",
                        InquiryOutcome::reply))
        .hasMessage("down");
  }

  @Test
  void nonAsciiRequestYields1000ByteReply() {
    processor.handle(
        new ChannelMessage("m9", "c9", "INQA0000000000\u00e9", Map.of()),
        "IN",
        "REPLY",
        p -> InquiryOutcome.reply(p));
    assertThat(channel.receive("REPLY", Duration.ZERO).orElseThrow().payload())
        .startsWith("INQA0000000000? ")
        .hasSize(1000);
  }

  @Test
  void multibyteRequestOver1000BytesIsTruncationError() {
    processor.handle(
        new ChannelMessage("m10", "c10", "INQA" + "\uD83D\uDE00".repeat(251), Map.of()),
        "IN",
        "REPLY",
        InquiryOutcome::reply);
    assertThat(channel.receive("ERR", Duration.ZERO).orElseThrow().payload())
        .contains("INP MQGET ERR:");
    assertThat(channel.receive("REPLY", Duration.ZERO)).isEmpty();
  }

  @Test
  void oversizedRequestFailsLikeTruncatedMqget() {
    processor.handle(
        new ChannelMessage("m4", "c4", "X".repeat(1001), Map.of()),
        "IN",
        "REPLY",
        InquiryOutcome::reply);
    assertThat(channel.receive("ERR", Duration.ZERO).orElseThrow().payload())
        .startsWith(" ".repeat(25) + "  INP MQGET ERR:             01  02080  IN ");
    assertThat(channel.receive("REPLY", Duration.ZERO)).isEmpty();
  }
}
