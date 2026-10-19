package com.carddemo.online.card.session;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.online.card.mapper.CardScreenSupport;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Pseudo-conversation storage (ADR-002): {@link ConversationContext} plus the program-private part
 * of the COMMAREA ({@code WS-THIS-PROGCOMMAREA}) kept in the HTTP session.
 */
@Component
public class CardConversation {

  /** Session attribute of the shared COCOM01Y context. */
  public static final String CONTEXT_ATTRIBUTE = ConversationContext.class.getName();

  static final String LIST_STATE = "o3-card.COCRDLIC";
  static final String UPDATE_STATE = "o3-card.COCRDUPC";
  static final String OWNED_ACCOUNT = "o3-card.ownedAccount";
  private static final java.util.Set<String> O3_PROGRAMS =
      java.util.Set.of("COCRDLIC", "COCRDSLC", "COCRDUPC");

  /**
   * Context of the session, or (while O1 has not stored one) the context of a user who signed on
   * and picked the option from the main menu COMEN01C.
   */
  public ConversationContext context(HttpSession session, Principal principal) {
    Object stored = session.getAttribute(CONTEXT_ATTRIBUTE);
    if (stored instanceof ConversationContext ctx) {
      rememberOwnedAccount(session, ctx);
      return ctx;
    }
    String user = principal == null ? null : principal.getName();
    return ContextEdit.of(
            ConversationContext.signedOn(user, isAdmin(principal) ? UserType.ADMIN : UserType.USER))
        .from(CardScreenSupport.MENU_TRANID, CardScreenSupport.MENU_PGM)
        .build();
  }

  /** True once a screen of the conversation has been sent in this session. */
  public boolean hasContext(HttpSession session) {
    return session.getAttribute(CONTEXT_ATTRIBUTE) instanceof ConversationContext;
  }

  public void store(HttpSession session, ConversationContext ctx) {
    session.setAttribute(CONTEXT_ATTRIBUTE, ctx);
  }

  /**
   * Account the non-admin user is confined to: the COMMAREA account of the first context seen in
   * the session (COCRDLIC header, rule b). Empty for admins or when no account is known.
   */
  public Optional<Long> ownedAccount(HttpSession session, ConversationContext ctx) {
    if (ctx.isAdmin()) {
      return Optional.empty();
    }
    rememberOwnedAccount(session, ctx);
    return Optional.ofNullable((Long) session.getAttribute(OWNED_ACCOUNT));
  }

  public CardListState listState(HttpSession session) {
    return session.getAttribute(LIST_STATE) instanceof CardListState s
        ? s
        : CardListState.initial();
  }

  public void storeListState(HttpSession session, CardListState state) {
    session.setAttribute(LIST_STATE, state);
  }

  public CardUpdateState updateState(HttpSession session) {
    return session.getAttribute(UPDATE_STATE) instanceof CardUpdateState s
        ? s
        : CardUpdateState.initial();
  }

  public void storeUpdateState(HttpSession session, CardUpdateState state) {
    session.setAttribute(UPDATE_STATE, state);
  }

  private static void rememberOwnedAccount(HttpSession session, ConversationContext ctx) {
    if (!ctx.isAdmin()
        && (ctx.fromProgram() == null || !O3_PROGRAMS.contains(ctx.fromProgram()))
        && session.getAttribute(OWNED_ACCOUNT) == null
        && ctx.accountId() != null
        && ctx.accountId() != 0) {
      session.setAttribute(OWNED_ACCOUNT, ctx.accountId());
    }
  }

  private static boolean isAdmin(Principal principal) {
    return principal instanceof Authentication auth
        && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "A".equals(a.getAuthority()));
  }
}
