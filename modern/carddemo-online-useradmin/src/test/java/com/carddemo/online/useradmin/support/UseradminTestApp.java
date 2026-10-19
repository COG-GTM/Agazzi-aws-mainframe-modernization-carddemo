package com.carddemo.online.useradmin.support;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.online.useradmin.store.JpaUserStore;
import com.carddemo.online.useradmin.store.UserStore;
import com.carddemo.platform.time.FixedBusinessClock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web slice of the module without a database: in-memory USRSEC, golden fixed clock and an HTTP
 * Basic chain (ADMIN001 = ROLE_ADMIN, USER0001 = ROLE_USER, ADMIN002 without roles) standing in for
 * the O1 sign-on.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class
    })
@ComponentScan(
    basePackages = "com.carddemo.online.useradmin",
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaUserStore.class),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "com\\.carddemo\\.online\\.useradmin\\.(support|store)\\..*IT.*")
    })
public class UseradminTestApp {

  @Bean
  UserStore userStore() {
    return InMemoryUserStore.seeded();
  }

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }

  @Bean
  UserCredentialService userCredentialService() {
    return new TestCredentials();
  }

  @Bean
  UserDetailsService users() {
    return new InMemoryUserDetailsManager(
        User.withUsername("ADMIN001").password("{noop}PASSWORD").roles("ADMIN").build(),
        User.withUsername("USER0001").password("{noop}PASSWORD").roles("USER").build(),
        User.withUsername("ADMIN002").password("{noop}PASSWORD").authorities("SCOPE_x").build(),
        User.withUsername("USER0002").password("{noop}PASSWORD").authorities("SCOPE_x").build());
  }

  @Bean
  SecurityFilterChain testChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .csrf(c -> c.disable());
    return http.build();
  }
}
