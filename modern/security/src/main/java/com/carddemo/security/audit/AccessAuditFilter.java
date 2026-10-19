package com.carddemo.security.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * One {@code ACCESS} audit record per API call that reaches the application (sign-on, admin
 * operations, updates, sensitive reads, report jobs). Only the AID key ({@code action}) is taken
 * from the body; field values (PAN, SSN, passwords) are never read into the log. Requests rejected
 * by Spring Security are audited by the AUTHENTICATION/AUTHORIZATION events instead.
 */
public class AccessAuditFilter extends OncePerRequestFilter {

  private static final Pattern ACTION = Pattern.compile("[A-Z0-9]{1,5}");
  private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

  /** BMS screen payloads are a few KiB; larger bodies are audited without {@code action}. */
  private static final int BODY_CACHE_LIMIT = 64 * 1024;

  private final AuditLog log;
  private final ObjectMapper mapper;
  private final boolean includeNavigation;

  public AccessAuditFilter(AuditLog log, ObjectMapper mapper, boolean includeNavigation) {
    this.log = log;
    this.mapper = mapper;
    this.includeNavigation = includeNavigation;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    AuditCategory category = AuditCategory.of(path);
    boolean firstSend = "GET".equals(request.getMethod()) && AuditCategory.screenOf(path) != null;
    boolean audited = includeNavigation || !(firstSend || category == AuditCategory.NAVIGATION);
    if (!audited) {
      chain.doFilter(request, response);
      return;
    }
    ContentCachingRequestWrapper wrapped =
        new ContentCachingRequestWrapper(request, BODY_CACHE_LIMIT);
    String requestId = requestId(request);
    response.setHeader("X-Request-Id", requestId);
    int status = 500;
    try {
      chain.doFilter(wrapped, response);
      status = response.getStatus();
    } finally {
      Map<String, Object> f = new LinkedHashMap<>();
      f.put("principal", principal());
      f.put("category", category);
      f.put("method", request.getMethod());
      f.put("path", path);
      f.put("screen", AuditCategory.screenOf(path));
      f.put("action", action(wrapped));
      f.put("status", status);
      f.put(
          "outcome",
          status < 400 ? "SUCCESS" : status == 401 || status == 403 ? "DENIED" : "FAILURE");
      f.put("remoteAddress", request.getRemoteAddr());
      f.put("requestId", requestId);
      log.record("ACCESS", f);
    }
  }

  private static String requestId(HttpServletRequest request) {
    String h = request.getHeader("X-Request-Id");
    return h != null && REQUEST_ID.matcher(h).matches() ? h : UUID.randomUUID().toString();
  }

  private static String principal() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    return a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated()
        ? "anonymous"
        : a.getName();
  }

  private String action(ContentCachingRequestWrapper request) {
    byte[] body = request.getContentAsByteArray();
    if (body.length == 0) {
      return null;
    }
    try {
      JsonNode a = mapper.readTree(body).get("action");
      return a != null && a.isTextual() && ACTION.matcher(a.asText()).matches() ? a.asText() : null;
    } catch (IOException e) {
      return null;
    }
  }
}
