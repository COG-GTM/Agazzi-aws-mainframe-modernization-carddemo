package com.carddemo.online.transaction.web;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.online.transaction.service.CommareaExtension;
import com.carddemo.online.transaction.service.Navigation;
import jakarta.servlet.http.HttpSession;

/**
 * Session storage of the COMMAREA (ADR-002): {@link ConversationContext} under its class name
 * (shared with the other online modules, written at sign-on by O1) and the O4 program area.
 */
public final class ConversationStore {

  /** Session attribute holding the {@link ConversationContext} (COCOM01Y). */
  public static final String CONTEXT_ATTRIBUTE = ConversationContext.class.getName();

  /** Session attribute holding the O4 {@link CommareaExtension}. */
  public static final String EXTENSION_ATTRIBUTE = CommareaExtension.class.getName();

  /** Last TRANREPT execution submitted by CR00 in this session. */
  public static final String REPORT_ATTRIBUTE = "com.carddemo.online.transaction.lastReportJob";

  private ConversationStore() {}

  public static ConversationContext context(HttpSession session) {
    return session == null
        ? null
        : session.getAttribute(CONTEXT_ATTRIBUTE) instanceof ConversationContext c ? c : null;
  }

  /**
   * The O4 program area: kept across XCTLs between O4 programs, reset when the screen is entered
   * from any other program (the menus pass a bare COCOM01Y).
   */
  public static CommareaExtension extension(HttpSession session, ConversationContext ctx) {
    Object stored = session.getAttribute(EXTENSION_ATTRIBUTE);
    boolean fromOutside =
        ctx.programContext() != ProgramContext.REENTER
            && (ctx.fromProgram() == null
                || !Navigation.O4_PROGRAMS.contains(ctx.fromProgram().strip()));
    if (fromOutside || !(stored instanceof CommareaExtension e)) {
      return CommareaExtension.initial();
    }
    return e;
  }

  public static void save(
      HttpSession session, ConversationContext ctx, CommareaExtension extension) {
    session.setAttribute(CONTEXT_ATTRIBUTE, ctx);
    session.setAttribute(EXTENSION_ATTRIBUTE, extension);
  }
}
