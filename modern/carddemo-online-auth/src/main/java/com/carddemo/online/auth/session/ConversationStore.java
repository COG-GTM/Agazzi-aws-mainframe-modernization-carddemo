package com.carddemo.online.auth.session;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.online.auth.security.CredentialAuthenticationProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * {@link ConversationContext} (COCOM01Y COMMAREA) kept in the server-side HTTP session (ADR-002,
 * Spring Session JDBC). Stored as JSON under {@link #ATTRIBUTE} so that every online module can
 * read it with Jackson without sharing a Serializable type.
 */
@Component
public class ConversationStore {

  /** Session attribute holding the JSON of the current {@link ConversationContext}. */
  public static final String ATTRIBUTE = "carddemo.conversationContext";

  private final ObjectMapper json;

  public ConversationStore(ObjectMapper json) {
    // ConversationContext#isAdmin() is serialized as "admin": ignore it (and future fields) on read
    this.json = json.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public Optional<ConversationContext> load(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    Object v = session == null ? null : session.getAttribute(ATTRIBUTE);
    if (!(v instanceof String s)) {
      return Optional.empty();
    }
    try {
      return Optional.of(json.readValue(s, ConversationContext.class));
    } catch (JsonProcessingException e) {
      return Optional.empty();
    }
  }

  /** Saves the context in the session; stateless clients (HTTP Basic, no session) are skipped. */
  public void save(HttpServletRequest request, ConversationContext ctx) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return;
    }
    if (ctx == null) {
      session.removeAttribute(ATTRIBUTE);
      return;
    }
    try {
      session.setAttribute(ATTRIBUTE, json.writeValueAsString(ctx));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("ConversationContext not serializable", e);
    }
  }

  /**
   * COMMAREA of the signed-on user: the session one when it belongs to the authenticated principal,
   * otherwise (HTTP Basic client, no session) a fresh sign-on context already in REENTER state. The
   * user id and type always come from the authenticated principal, never from the request.
   */
  public ConversationContext resolve(HttpServletRequest request, Authentication auth) {
    String user = auth.getName();
    var type = CredentialAuthenticationProvider.userType(auth);
    return load(request)
        .filter(c -> user.equals(c.userId()) && type == c.userType())
        .orElseGet(
            () -> {
              ConversationContext c = ConversationContext.signedOn(user, type);
              return c.programContext() == ProgramContext.REENTER ? c : c.reenter();
            });
  }
}
