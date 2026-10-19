package com.carddemo.ext.authorization;

import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.platform.messaging.InMemoryMessageChannel;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal host for the X1 beans: domain + X1 entities, in-memory MQ, settable business clock. */
@SpringBootApplication(scanBasePackages = "com.carddemo.ext.authorization")
@EntityScan(basePackages = {"com.carddemo.domain", "com.carddemo.ext.authorization"})
@EnableJpaRepositories(basePackages = {"com.carddemo.domain", "com.carddemo.ext.authorization"})
public class TestAuthorizationApplication {

  @Bean
  MutableBusinessClock businessClock() {
    return new MutableBusinessClock(LocalDateTime.of(2022, 7, 18, 12, 0));
  }

  @Bean
  MessageChannelPort messageChannel() {
    return new InMemoryMessageChannel();
  }
}
