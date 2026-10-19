package com.carddemo.online.transaction.service;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import java.util.Arrays;
import java.util.Set;

/** XCTL targets: program name (CDEMO-TO-PROGRAM) to screen, and the O4 programs. */
public final class Navigation {

  /** Programs owned by O4: the commarea extension survives XCTLs between them. */
  public static final Set<String> O4_PROGRAMS =
      Set.of("COTRN00C", "COTRN01C", "COTRN02C", "COBIL00C", "CORPT00C");

  private Navigation() {}

  public static ScreenId screenOf(String program) {
    String p = program == null ? "" : program.strip();
    return Arrays.stream(ScreenId.values())
        .filter(s -> s.program().equals(p))
        .findFirst()
        .orElse(ScreenId.COSGN00);
  }

  /** RETURN-TO-PREV-SCREEN: FROM = this program, PGM-CONTEXT = 0, XCTL to {@code toProgram}. */
  public static ConversationContext xctl(
      ConversationContext ctx, String fromTran, String fromPgm, String toProgram) {
    String to = CobolFmt.blank(toProgram) ? "COSGN00C" : toProgram.strip();
    ScreenId target = screenOf(to);
    return ctx.transferTo(fromTran, fromPgm, target.transactionId(), to);
  }

  /** Same COMMAREA with CDEMO-PGM-CONTEXT forced to ENTER (GET = first send). */
  public static ConversationContext enter(ConversationContext ctx) {
    return ctx.transferTo(
        ctx.fromTransactionId(), ctx.fromProgram(), ctx.toTransactionId(), ctx.toProgram());
  }
}
