package com.carddemo.online.card.support;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.platform.time.FixedBusinessClock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Module-local boot of the card screens on PostgreSQL (V1 + seed V2), with the Fase 0 security
 * chain and the DUSRSECJ seed users (ADMIN00n = admin, USER000n = user).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan("com.carddemo.online.card")
@EntityScan("com.carddemo.domain")
@EnableJpaRepositories("com.carddemo.domain")
public class O3TestApplication {

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }

  @Bean
  SecurityFilterChain o3TestChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .httpBasic(b -> {})
        .csrf(c -> c.disable());
    return http.build();
  }

  @Bean
  InMemoryUserDetailsManager seedUsers() {
    return new InMemoryUserDetailsManager(
        User.withUsername("ADMIN001").password("{noop}PASSWORD").roles("ADMIN").build(),
        User.withUsername("USER0001").password("{noop}PASSWORD").roles("USER").build(),
        User.withUsername("USER0002").password("{noop}PASSWORD").roles("USER").build());
  }
}
