package com.carddemo.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Fase 0 security: health/info and the OpenAPI document are public, everything else requires
 * authentication. O1 replaces it by declaring its own {@link SecurityFilterChain} (which must keep
 * {@code /actuator/health} public for docker-compose and the load balancer).
 */
@AutoConfiguration(before = SecurityAutoConfiguration.class)
public class Fase0SecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain fase0SecurityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**", "/actuator/info", "/openapi/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .httpBasic(b -> {})
        .csrf(c -> c.disable());
    return http.build();
  }
}
