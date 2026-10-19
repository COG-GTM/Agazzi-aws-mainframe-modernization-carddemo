package com.carddemo.online.auth.security;

import com.carddemo.contracts.port.UserCredentialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * O1 security (replaces {@code Fase0SecurityAutoConfiguration}): users of {@code app_user} (RACF +
 * USRSEC), roles ADMIN/USER from SEC-USR-TYPE, server-side session (ADR-002) created by the CC00
 * sign-on screen, HTTP Basic kept for machine clients and golden scenarios. CA00 and CU00-CU03
 * require ADMIN (plan Fase 3, PARITY-EXCEPTION O1-PE-2).
 *
 * <p>CSRF is active for cookie sessions (SPA, k6): double-submit cookie {@code XSRF-TOKEN} echoed
 * in {@code X-XSRF-TOKEN}. Requests carrying an {@code Authorization} header (HTTP Basic machine
 * clients) are exempt, as a browser never attaches it cross-site. The chain exists only in a
 * servlet context: batch runs ({@code spring.main.web-application-type=none}) start without it.
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
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
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
                    .requestMatchers(HttpMethod.POST, "/actuator/maintenance")
                    .hasRole("ADMIN")
                    .requestMatchers(ADMIN_SCREENS)
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .httpBasic(b -> b.authenticationEntryPoint(handlers))
        .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
        .formLogin(f -> f.disable())
        .logout(l -> l.disable())
        .csrf(
            c ->
                c.csrfTokenRepository(csrfTokens())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers(r -> r.getHeader(HttpHeaders.AUTHORIZATION) != null))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
    return http.build();
  }

  static CookieCsrfTokenRepository csrfTokens() {
    CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repo.setCookieCustomizer(c -> c.sameSite("Strict"));
    return repo;
  }

  /** Loads the deferred token on every request so the SPA always has the XSRF-TOKEN cookie. */
  static final class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
        token.getToken();
      }
      chain.doFilter(request, response);
    }
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
