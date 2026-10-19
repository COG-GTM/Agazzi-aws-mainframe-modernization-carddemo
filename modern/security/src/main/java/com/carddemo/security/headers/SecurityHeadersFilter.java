package com.carddemo.security.headers;

import com.carddemo.security.SecurityHardeningProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security response headers (ASVS 14.4.x) independent of the SecurityFilterChain: CSP,
 * Referrer-Policy, Permissions-Policy, COOP/CORP, nosniff, frame denial, no-store on the API, and
 * HSTS on requests that arrived over TLS (directly or via {@code X-Forwarded-Proto} with {@code
 * server.forward-headers-strategy}).
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

  private final SecurityHardeningProperties.Headers config;
  private final String hsts;

  public SecurityHeadersFilter(SecurityHardeningProperties.Headers config) {
    this.config = config;
    StringBuilder sb = new StringBuilder("max-age=").append(config.getHstsMaxAge().toSeconds());
    if (config.isHstsIncludeSubDomains()) {
      sb.append("; includeSubDomains");
    }
    if (config.isHstsPreload()) {
      sb.append("; preload");
    }
    this.hsts = sb.toString();
  }

  public String hstsValue() {
    return hsts;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Content-Security-Policy", config.getContentSecurityPolicy());
    response.setHeader("Referrer-Policy", config.getReferrerPolicy());
    response.setHeader("Permissions-Policy", config.getPermissionsPolicy());
    response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
    response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("Pragma", "no-cache");
    }
    if (request.isSecure()) {
      response.setHeader("Strict-Transport-Security", hsts);
    }
    chain.doFilter(request, response);
  }
}
