package com.carddemo.itauth;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.online.auth.AuthTestApplication;
import com.carddemo.platform.time.FixedBusinessClock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** O1 on a real PostgreSQL: Flyway V1 + seed + V1000 (Spring Session JDBC), JPA app_user. */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "com.carddemo.online.auth",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = AuthTestApplication.class))
@EntityScan(basePackages = "com.carddemo.domain")
@EnableJpaRepositories(basePackages = "com.carddemo.domain")
public class AuthItApplication {

  @Bean
  BusinessClock businessClock() {
    return new FixedBusinessClock(AuthTestApplication.BUSINESS_TIME);
  }
}
