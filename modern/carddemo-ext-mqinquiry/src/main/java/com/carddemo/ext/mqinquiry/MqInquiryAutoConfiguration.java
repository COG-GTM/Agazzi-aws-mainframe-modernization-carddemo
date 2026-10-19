package com.carddemo.ext.mqinquiry;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.domain.repository.AccountRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** x3-mqinquiry wiring, active only with {@code carddemo.extensions.mqinquiry.enabled=true}. */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "carddemo.extensions.mqinquiry",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(MqInquiryProperties.class)
@Import(MqInquiryController.class)
public class MqInquiryAutoConfiguration {

  @Bean
  SystemDateInquiry mqInquirySystemDate(BusinessClock clock) {
    return new SystemDateInquiry(clock);
  }

  @Bean
  AccountInquiry mqInquiryAccount(AccountRepository accounts, MqInquiryProperties properties) {
    return new AccountInquiry(accounts, properties.accountRequestQueue());
  }

  @Bean
  MqInquiryProcessor mqInquiryProcessor(
      MessageChannelPort channel, MqInquiryProperties properties) {
    return new MqInquiryProcessor(channel, properties.errorQueue());
  }

  @Bean
  MqInquiryListener mqInquiryListener(
      MessageChannelPort channel,
      MqInquiryProcessor processor,
      MqInquiryProperties properties,
      SystemDateInquiry systemDate,
      AccountInquiry account) {
    return new MqInquiryListener(channel, processor, properties, systemDate, account);
  }
}
