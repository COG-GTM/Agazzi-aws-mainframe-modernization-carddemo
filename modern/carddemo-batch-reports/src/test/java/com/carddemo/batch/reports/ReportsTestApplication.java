package com.carddemo.batch.reports;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.platform.time.FixedBusinessClock;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Minimal application: the B3 jobs and launcher on an embedded H2 job repository. */
@SpringBootApplication
public class ReportsTestApplication {

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }
}
