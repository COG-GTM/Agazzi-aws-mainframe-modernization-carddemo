package com.carddemo.platform.config;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.WaitService;
import com.carddemo.platform.time.FixedBusinessClock;
import com.carddemo.platform.time.ProductionBusinessClock;
import com.carddemo.platform.wait.VirtualThreadWaitService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Workstream 1C production time and wait services. Runs before the Fase 0 fallbacks of {@code
 * carddemo-app}, so these beans replace {@code SystemBusinessClock}/{@code SleepingWaitService}
 * while any bean declared by an application or test still wins.
 *
 * <p>{@code carddemo.clock.fixed} (ISO local date-time) keeps the frozen golden clock used by the
 * {@code test} profile; {@code carddemo.clock.zone} sets the business (CICS region) time zone.
 */
@AutoConfiguration(beforeName = "com.carddemo.app.fallback.Fase0FallbackPortsAutoConfiguration")
public class PlatformTimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  BusinessClock businessClock(
      @Value("${carddemo.clock.fixed:}") String fixed,
      @Value("${carddemo.clock.zone:}") String zone) {
    if (!fixed.isBlank()) {
      return new FixedBusinessClock(LocalDateTime.parse(fixed));
    }
    return new ProductionBusinessClock(zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone));
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  WaitService waitService() {
    return new VirtualThreadWaitService();
  }
}
