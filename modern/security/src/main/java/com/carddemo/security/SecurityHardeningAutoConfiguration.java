package com.carddemo.security;

import com.carddemo.security.audit.AccessAuditFilter;
import com.carddemo.security.audit.AuditLog;
import com.carddemo.security.audit.SecurityEventAuditListener;
import com.carddemo.security.audit.StructuredAuditEventRepository;
import com.carddemo.security.headers.SecurityHeadersFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.autoconfigure.audit.AuditAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;

/**
 * Fase 5C hardening, additive to the O1 {@code SecurityFilterChain}. Runs before {@link
 * AuditAutoConfiguration} so that Boot's authentication/authorization audit listeners are created
 * against {@link StructuredAuditEventRepository}.
 */
@AutoConfiguration(before = AuditAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityHardeningProperties.class)
public class SecurityHardeningAutoConfiguration {

  /** Before Spring Security, so 401/403 responses carry the headers too. */
  static final int HEADERS_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

  /** Inside the security chain's span (after it), so the principal is known. */
  static final int AUDIT_FILTER_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER + 10;

  @Bean
  @ConditionalOnProperty(
      prefix = "carddemo.security.headers",
      name = "enabled",
      matchIfMissing = true)
  FilterRegistrationBean<SecurityHeadersFilter> carddemoSecurityHeadersFilter(
      SecurityHardeningProperties props) {
    var reg = new FilterRegistrationBean<>(new SecurityHeadersFilter(props.getHeaders()));
    reg.setOrder(HEADERS_FILTER_ORDER);
    reg.addUrlPatterns("/*");
    return reg;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      prefix = "carddemo.security.audit",
      name = "enabled",
      matchIfMissing = true)
  static class AuditConfiguration {

    @Bean
    AuditLog carddemoAuditLog() {
      return new AuditLog();
    }

    @Bean
    @ConditionalOnMissingBean(AuditEventRepository.class)
    StructuredAuditEventRepository carddemoAuditEventRepository(AuditLog log) {
      return new StructuredAuditEventRepository(log);
    }

    /** Spring Security publishes AuthorizationDeniedEvent only when this bean exists. */
    @Bean
    @ConditionalOnMissingBean(AuthorizationEventPublisher.class)
    AuthorizationEventPublisher carddemoAuthorizationEventPublisher(
        ApplicationEventPublisher publisher) {
      return new SpringAuthorizationEventPublisher(publisher);
    }

    @Bean
    SecurityEventAuditListener carddemoSecurityEventAuditListener(AuditLog log) {
      return new SecurityEventAuditListener(log);
    }

    @Bean
    FilterRegistrationBean<AccessAuditFilter> carddemoAccessAuditFilter(
        AuditLog log, SecurityHardeningProperties props, ObjectMapper mapper) {
      var reg =
          new FilterRegistrationBean<>(
              new AccessAuditFilter(log, mapper, props.getAudit().isIncludeNavigation()));
      reg.setOrder(AUDIT_FILTER_ORDER);
      reg.addUrlPatterns("/api/*", "/actuator/maintenance", "/actuator/maintenance/*");
      return reg;
    }
  }
}
