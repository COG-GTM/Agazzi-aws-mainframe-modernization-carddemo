package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import com.carddemo.platform.messaging.InMemoryMessageChannel;
import java.time.Duration;
import java.util.Map;
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
