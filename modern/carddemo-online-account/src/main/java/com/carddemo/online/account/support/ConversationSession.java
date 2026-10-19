package com.carddemo.online.account.support;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Local adapter for the COMMAREA kept in the HTTP session (plan §3.3): the shared {@link
 * ConversationContext} plus the program-specific part ({@code WS-THIS-PROGCOMMAREA}). The shared
 * attribute name is proposed to online-common as a CONTRACT-CHANGE.
 */
public final class ConversationSession {

  /** Session attribute holding {@code CARDDEMO-COMMAREA}. */
  public static final String CONTEXT_ATTRIBUTE = ConversationContext.SESSION_ATTRIBUTE;

  private ConversationSession() {}

  /** Current context, or a freshly signed-on one derived from the authenticated principal. */
  public static ConversationContext current(HttpSession session, Principal principal) {
    Object stored = session.getAttribute(CONTEXT_ATTRIBUTE);
    String user = principal == null ? null : principal.getName();
    if (stored instanceof ConversationContext ctx
        && (user == null || user.equalsIgnoreCase(String.valueOf(ctx.userId()).strip()))) {
      return ctx;
    }
    return ConversationContext.signedOn(user, userType(principal));
  }

  public static void store(HttpSession session, ConversationContext ctx) {
    session.setAttribute(CONTEXT_ATTRIBUTE, ctx);
  }

  /** SEC-USR-TYPE from the Spring Security authorities ({@code ROLE_ADMIN}/{@code ADMIN}). */
  public static UserType userType(Principal principal) {
    if (principal instanceof Authentication auth) {
      for (GrantedAuthority a : auth.getAuthorities()) {
        String name = a.getAuthority();
        if ("ROLE_ADMIN".equals(name) || "ADMIN".equals(name)) {
          return UserType.ADMIN;
        }
      }
    }
    return UserType.USER;
  }
}
