package com.carddemo.online.auth.session;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.online.auth.security.CredentialAuthenticationProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * {@link ConversationContext} (COCOM01Y COMMAREA) kept in the server-side HTTP session (ADR-002,
 * Spring Session JDBC) under {@link ConversationContext#SESSION_ATTRIBUTE}, the key shared by every
 * online module (contracts-v1.1).
 */
@Component
public class ConversationStore {

  /** Session attribute holding the current {@link ConversationContext}. */
  public static final String ATTRIBUTE = ConversationContext.SESSION_ATTRIBUTE;

  public Optional<ConversationContext> load(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    Object v = session == null ? null : session.getAttribute(ATTRIBUTE);
    return v instanceof ConversationContext c ? Optional.of(c) : Optional.empty();
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
    session.setAttribute(ATTRIBUTE, ctx);
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
