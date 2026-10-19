package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.platform.messaging.InMemoryMessageChannel;
import com.carddemo.platform.time.FixedBusinessClock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MqInquiryListenerTest {

  private final MqInquiryProperties props =
      new MqInquiryProperties(true, true, null, null, null, null, null, Duration.ofMillis(50));

  private MqInquiryListener listener(MessageChannelPort channel) {
    AccountRepository repo = mock(AccountRepository.class);
    when(repo.findById(anyLong())).thenReturn(Optional.empty());
    return new MqInquiryListener(
        channel,
        new MqInquiryProcessor(channel, props.errorQueue()),
        props,
        new SystemDateInquiry(FixedBusinessClock.golden()),
        new AccountInquiry(repo, props.accountRequestQueue()));
  }

  @Test
  void answersRequestReplyOnBothQueues() {
    InMemoryMessageChannel channel = new InMemoryMessageChannel();
    MqInquiryListener listener = listener(channel);
    assertThat(listener.isAutoStartup()).isTrue();
    listener.start();
    listener.start();
    try {
      assertThat(listener.isRunning()).isTrue();
      Optional<ChannelMessage> date =
          channel.request(
              props.dateRequestQueue(), props.dateReplyQueue(), "DATE", Duration.ofSeconds(5));
      assertThat(date.orElseThrow().payload())
          .startsWith("SYSTEM DATE : 07-18-2022SYSTEM TIME : 12:00:00");
      Optional<ChannelMessage> acct =
          channel.request(
              props.accountRequestQueue(),
              props.accountReplyQueue(),
              "INQA00000000042",
              Duration.ofSeconds(5));
      assertThat(acct.orElseThrow().payload())
          .startsWith("INVALID REQUEST PARAMETERS ACCT ID : 00000000042 ");
    } finally {
      listener.stop();
    }
    assertThat(listener.isRunning()).isFalse();
  }

  @Test
  void survivesTransportFailures() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    MessageChannelPort failing =
        new MessageChannelPort() {
          @Override
          public String send(String channel, String payload, Map<String, String> headers) {
            return "x";
          }

          @Override
          public Optional<ChannelMessage> receive(String channel, Duration wait) {
            calls.incrementAndGet();
            throw new IllegalStateException("transport down");
          }
        };
    MqInquiryListener listener = listener(failing);
    listener.start();
    Thread.sleep(200);
    listener.stop();
    assertThat(calls.get()).isGreaterThanOrEqualTo(2);
  }
}
