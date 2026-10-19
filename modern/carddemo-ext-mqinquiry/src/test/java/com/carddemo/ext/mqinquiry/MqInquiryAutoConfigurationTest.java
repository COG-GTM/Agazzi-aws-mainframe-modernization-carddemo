package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.platform.messaging.InMemoryMessageChannel;
import com.carddemo.platform.time.FixedBusinessClock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MqInquiryAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MqInquiryAutoConfiguration.class))
          .withBean(MessageChannelPort.class, InMemoryMessageChannel::new)
          .withBean(BusinessClock.class, FixedBusinessClock::golden)
          .withBean(AccountRepository.class, () -> mock(AccountRepository.class));

  @Test
  void offByDefault() {
    runner.run(
        ctx -> {
          assertThat(ctx).doesNotHaveBean(MqInquiryListener.class);
          assertThat(ctx).doesNotHaveBean(MqInquiryController.class);
          assertThat(ctx).doesNotHaveBean(MqInquiryProperties.class);
        });
    runner
        .withPropertyValues("carddemo.extensions.mqinquiry.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(MqInquiryListener.class));
  }

  @Test
  void onWiresListenerAndRestTwin() {
    runner
        .withPropertyValues(
            "carddemo.extensions.mqinquiry.enabled=true",
            "carddemo.extensions.mqinquiry.listener-enabled=false",
            "carddemo.extensions.mqinquiry.account-reply-queue=ACCT-OUT")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(MqInquiryListener.class);
              assertThat(ctx).hasSingleBean(MqInquiryController.class);
              MqInquiryProperties p = ctx.getBean(MqInquiryProperties.class);
              assertThat(p.accountReplyQueue()).isEqualTo("ACCT-OUT");
              assertThat(p.dateReplyQueue()).isEqualTo("CARD-DEMO-REPLY-DATE");
              assertThat(p.pollWait().toMillis()).isEqualTo(5000);
              assertThat(ctx.getBean(MqInquiryListener.class).isRunning()).isFalse();
            });
  }

  @Test
  void defaultsMatchLegacyQueuesInSqsSyntax() {
    MqInquiryProperties d = MqInquiryProperties.defaults();
    assertThat(d.enabled()).isFalse();
    assertThat(d.listenerEnabled()).isTrue();
    assertThat(d.honorReplyTo()).isFalse();
    assertThat(d.errorQueue()).isEqualTo("CARD-DEMO-ERROR");
    assertThat(d.accountRequestQueue()).isEqualTo("CARD-DEMO-REQUEST-ACCT");
  }
}
