package com.carddemo.ext.trantype.online;

import com.carddemo.ext.trantype.TrantypeEnabled;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Pseudo-conversational state of CTLI/CTTU. Browser clients keep it in the HTTP session (ADR-002);
 * cookie-less API clients (HTTP Basic, e.g. the parity runner) get one conversation per signed-on
 * user, like one CICS terminal per user.
 */
@Component
@TrantypeEnabled
public class TrantypeConversations {

  private final Map<String, Object> byUser = new ConcurrentHashMap<>();

  @SuppressWarnings("unchecked")
  <T> T get(HttpServletRequest request, String key, Supplier<T> factory) {
    if (perUser(request)) {
      return (T)
          byUser.computeIfAbsent(
              key + ":" + request.getUserPrincipal().getName(), k -> factory.get());
    }
    HttpSession session = request.getSession(true);
    Object s = session.getAttribute(key);
    if (s == null) {
      s = factory.get();
      session.setAttribute(key, s);
    }
    return (T) s;
  }

  /**
   * Writes the state back after the screen mutated it: Spring Session JDBC persists only attributes
   * passed to {@code setAttribute} (save mode {@code on_set_attribute}), not in-place changes.
   */
  void save(HttpServletRequest request, String key, Object state) {
    if (!perUser(request)) {
      request.getSession(true).setAttribute(key, state);
    }
  }

  private static boolean perUser(HttpServletRequest request) {
    Principal user = request.getUserPrincipal();
    boolean cookieSession =
        request.getRequestedSessionId() != null && request.isRequestedSessionIdValid();
    return user != null && !cookieSession;
  }
}
