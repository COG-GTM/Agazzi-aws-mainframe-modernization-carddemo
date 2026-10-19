package com.carddemo.online.auth.web;

import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.online.auth.security.CredentialAuthenticationProvider;
import com.carddemo.online.auth.service.ScreenResult;
import com.carddemo.online.auth.session.ConversationStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/** Applies the session effect of a {@link ScreenResult} (sign-on, sign-off, keep COMMAREA). */
@Component
public class ScreenSession {

  private final ConversationStore conversations;
  private final HttpSessionSecurityContextRepository contexts;

  public ScreenSession(
      ConversationStore conversations, HttpSessionSecurityContextRepository contexts) {
    this.conversations = conversations;
    this.contexts = contexts;
  }

  public <T> ScreenResponse<T> apply(
      ScreenResult<T> result, HttpServletRequest request, HttpServletResponse response) {
    switch (result.effect()) {
      case SIGN_ON -> {
        // new session id on sign-on (session fixation), principal + COMMAREA in the session
        if (request.getSession(false) != null) {
          request.changeSessionId();
        } else {
          request.getSession(true);
        }
        SecurityContext sc = SecurityContextHolder.createEmptyContext();
        sc.setAuthentication(CredentialAuthenticationProvider.token(result.user()));
        SecurityContextHolder.setContext(sc);
        contexts.saveContext(sc, request, response);
        conversations.save(request, result.response().context());
      }
      case SIGN_OFF -> {
        HttpSession session = request.getSession(false);
        if (session != null) {
          session.invalidate();
        }
        SecurityContextHolder.clearContext();
      }
      case KEEP -> {
        if (result.response().context() != null) {
          conversations.save(request, result.response().context());
        }
      }
      default -> throw new IllegalStateException(result.effect().name());
    }
    return result.response();
  }
}
