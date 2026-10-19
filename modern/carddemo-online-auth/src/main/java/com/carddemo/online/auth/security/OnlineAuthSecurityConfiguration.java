package com.carddemo.online.auth.security;

import com.carddemo.contracts.port.UserCredentialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * O1 security (replaces {@code Fase0SecurityAutoConfiguration}): users of {@code app_user} (RACF +
 * USRSEC), roles ADMIN/USER from SEC-USR-TYPE, server-side session (ADR-002) created by the CC00
 * sign-on screen, HTTP Basic kept for machine clients and golden scenarios. CA00 and CU00-CU03
 * require ADMIN (plan Fase 3, PARITY-EXCEPTION O1-PE-2).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class OnlineAuthSecurityConfiguration {

  /** Admin-only screen resources (CA00 COADM01C, CU00-CU03 COUSR00C-COUSR03C). */
  public static final String[] ADMIN_SCREENS = {
    "/api/v1/screens/coadm01",
    "/api/v1/screens/cousr00",
    "/api/v1/screens/cousr01",
    "/api/v1/screens/cousr02",
    "/api/v1/screens/cousr03"
  };

  @Bean
  CredentialAuthenticationProvider credentialAuthenticationProvider(
      UserCredentialService credentials) {
    return new CredentialAuthenticationProvider(credentials);
  }

  @Bean
  ScreenSecurityHandlers screenSecurityHandlers(ObjectMapper json) {
    return new ScreenSecurityHandlers(json);
  }

  @Bean
  HttpSessionSecurityContextRepository httpSessionSecurityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain onlineAuthSecurityFilterChain(
      HttpSecurity http,
      ScreenSecurityHandlers handlers,
      HttpSessionSecurityContextRepository sessionContexts,
      CredentialAuthenticationProvider provider)
      throws Exception {
    SecurityContextRepository contexts =
        new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(), sessionContexts);
    http.authenticationProvider(provider)
        .securityContext(c -> c.securityContextRepository(contexts))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**", "/actuator/info", "/openapi/**", "/error")
                    .permitAll()
                    .requestMatchers("/api/v1/screens/cosgn00")
                    .permitAll()
                    .requestMatchers(ADMIN_SCREENS)
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .httpBasic(b -> b.authenticationEntryPoint(handlers))
        .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
        .formLogin(f -> f.disable())
        .logout(l -> l.disable())
        // JSON API used by the SPA with a SameSite=Strict session cookie (ADR-002)
        .csrf(c -> c.disable());
    return http.build();
  }

  /** Session cookie of ADR-002: HttpOnly + SameSite=Strict. */
  @Bean
  DefaultCookieSerializerCustomizer carddemoSessionCookie() {
    return s -> {
      s.setUseHttpOnlyCookie(true);
      s.setSameSite("Strict");
    };
  }
}
