package com.carddemo.online.useradmin.session;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.online.useradmin.store.UserRow;
import com.carddemo.online.useradmin.store.UserStore;
import com.carddemo.online.useradmin.support.Fields;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Loads and stores the pseudo-conversation of CU00-CU03 in the HTTP session: the shared {@link
 * ConversationContext} (attribute {@link #CONTEXT_ATTRIBUTE}) and the CU commarea tail {@link
 * UseradminState}. Only {@code ADMIN} users may run the programs (403 otherwise).
 */
@Component
public class UseradminSession {

  public static final String CONTEXT_ATTRIBUTE = ConversationContext.class.getName();
  public static final String STATE_ATTRIBUTE = UseradminState.class.getName();

  private final UserStore users;

  public UseradminSession(UserStore users) {
    this.users = users;
  }

  /** Session state of one CU task. */
  public record Conversation(ConversationContext context, UseradminState state) {}

  /** Signed-on admin conversation, or 401 (not signed on) / 403 (not an admin). */
  public Conversation open(HttpSession session, Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String userId = auth.getName().trim().toUpperCase(Locale.ROOT);
    ConversationContext stored =
        session.getAttribute(CONTEXT_ATTRIBUTE) instanceof ConversationContext c
                && c.userId() != null
                && userId.equals(c.userId().trim().toUpperCase(Locale.ROOT))
            ? c
            : null;
    UserType type = userType(auth, userId);
    ConversationContext ctx =
        stored != null && stored.userType() == type
            ? stored
            : ConversationContext.signedOn(userId, type);
    if (!ctx.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    UseradminState state =
        stored != null && session.getAttribute(STATE_ATTRIBUTE) instanceof UseradminState s
            ? s
            : new UseradminState();
    return new Conversation(ctx, state);
  }

  public void save(HttpSession session, ConversationContext ctx, UseradminState state) {
    session.setAttribute(CONTEXT_ATTRIBUTE, ctx);
    session.setAttribute(STATE_ATTRIBUTE, state);
  }

  /** CDEMO-USER-TYPE from the granted roles, else the current SEC-USR-TYPE of USRSEC. */
  private UserType userType(Authentication auth, String userId) {
    for (GrantedAuthority a : auth.getAuthorities()) {
      String role = a.getAuthority();
      if ("ROLE_ADMIN".equals(role) || "ADMIN".equals(role)) {
        return UserType.ADMIN;
      }
    }
    for (GrantedAuthority a : auth.getAuthorities()) {
      String role = a.getAuthority();
      if ("ROLE_USER".equals(role) || "USER".equals(role)) {
        return UserType.USER;
      }
    }
    Optional<UserRow> row;
    try {
      row = users.findById(Fields.pad(userId, 8));
    } catch (RuntimeException e) {
      return UserType.USER;
    }
    return row.map(r -> "A".equals(r.type()) ? UserType.ADMIN : UserType.USER)
        .orElse(UserType.USER);
  }
}
