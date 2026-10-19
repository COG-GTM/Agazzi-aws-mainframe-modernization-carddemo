package com.carddemo.ext.trantype.it;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.platform.time.FixedBusinessClock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal host for the X2 module (carddemo-app cannot be a dependency: reactor cycle). */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "com.carddemo.ext.trantype",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.carddemo\\.ext\\.trantype\\.(it|support|parity)\\..*"))
@EntityScan("com.carddemo.domain.entity")
@EnableJpaRepositories("com.carddemo.domain.repository")
public class TrantypeItApplication {

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }
}
