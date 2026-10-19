package com.carddemo.online.auth;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.platform.time.FixedBusinessClock;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Slice of the app for module tests: O1 beans only, no database (app_user is mocked). */
@SpringBootApplication(
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class,
      SessionAutoConfiguration.class
    })
public class AuthTestApplication {

  /** Business date of the golden harness (profile {@code test}). */
  public static final LocalDateTime BUSINESS_TIME = LocalDateTime.of(2022, 7, 18, 12, 0);

  @Bean
  BusinessClock businessClock() {
    return new FixedBusinessClock(BUSINESS_TIME);
  }
}
