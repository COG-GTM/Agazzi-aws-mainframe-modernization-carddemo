package com.carddemo.ext.authorization.web;

import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Per-user pseudo-conversational state (one terminal per principal). */
@Component
@ConditionalOnAuthorizationExtension
public class AuthViewStateStore {
  private final Map<String, AuthViewState> states = new ConcurrentHashMap<>();

  public AuthViewState get(String user) {
    return states.computeIfAbsent(user, u -> new AuthViewState());
  }

  /** Drops the terminal state of {@code user} (new pseudo-conversation). */
  public void reset(String user) {
    states.remove(user);
  }
}
