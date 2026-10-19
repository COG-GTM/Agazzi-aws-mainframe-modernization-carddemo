package com.carddemo.online.account.testapp;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.platform.time.FixedBusinessClock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Minimal app for the module tests: O2 beans, domain JPA, HTTP Basic users of DUSRSECJ. */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.carddemo.online.account")
@EntityScan(basePackages = "com.carddemo.domain")
@EnableJpaRepositories(basePackages = "com.carddemo.domain")
public class AccountTestApplication {

  /** Test seam between the 9700 check and the REWRITEs (null action = no-op). */
  public static final class RewriteHook
      implements com.carddemo.online.account.service.AccountUpdateWriter.BeforeRewrite {
    public volatile java.util.function.LongConsumer action;

    @Override
    public void beforeRewrite(long acctId) {
      java.util.function.LongConsumer a = action;
      if (a != null) {
        a.accept(acctId);
      }
    }
  }

  @Bean
  RewriteHook rewriteHook() {
    return new RewriteHook();
  }

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }

  @Bean
  SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .csrf(c -> c.disable());
    return http.build();
  }

  @Bean
  InMemoryUserDetailsManager users() {
    return new InMemoryUserDetailsManager(
        User.withUsername("ADMIN001").password("{noop}PASSWORD").roles("ADMIN").build(),
        User.withUsername("USER0001").password("{noop}PASSWORD").roles("USER").build());
  }
}
