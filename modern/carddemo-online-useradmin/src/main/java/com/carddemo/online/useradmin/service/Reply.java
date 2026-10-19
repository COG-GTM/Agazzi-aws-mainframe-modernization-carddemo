package com.carddemo.online.useradmin.service;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.online.useradmin.session.UseradminState;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * WS-MESSAGE / WS-ERR-FLG / cursor field / XCTL target of one task of a COUSR0xC program. The last
 * SEND MAP wins, so every setter replaces the previous message.
 */
final class Reply {

  /** Screens that receive the CU tail of the commarea when control is transferred to them. */
  private static final Set<ScreenId> USERADMIN =
      EnumSet.of(ScreenId.COUSR00, ScreenId.COUSR01, ScreenId.COUSR02, ScreenId.COUSR03);

  private enum Kind {
    NONE,
    INFO,
    WARNING,
    ERROR
  }

  private String message = "";
  private Kind kind = Kind.NONE;
  private String field;
  private ScreenId next;

  /** {@code MOVE 'Y' TO WS-ERR-FLG} + message + {@code MOVE -1 TO <field>L}. */
  void error(String text, String cursorField) {
    message = text;
    kind = Kind.ERROR;
    field = cursorField;
  }

  /** Red message without WS-ERR-FLG (e.g. invalid selection, "Please modify to update"). */
  void warning(String text) {
    message = text;
    kind = Kind.WARNING;
    field = null;
  }

  /** Informational message (DFHGREEN / DFHNEUTR, browse notices). */
  void info(String text) {
    message = text;
    kind = Kind.INFO;
    field = null;
  }

  /** {@code MOVE SPACES TO WS-MESSAGE}. */
  void clear() {
    message = "";
    kind = Kind.NONE;
    field = null;
  }

  boolean errorFlag() {
    return kind == Kind.ERROR;
  }

  String message() {
    return message;
  }

  /** {@code XCTL PROGRAM(CDEMO-TO-PROGRAM)}. */
  void transferTo(ScreenId target) {
    next = target;
  }

  /** XCTL to {@code CDEMO-FROM-PROGRAM}, or COADM01C when it is SPACES / LOW-VALUES. */
  void returnToCaller(ConversationContext ctx) {
    String from = ctx.fromProgram() == null ? "" : ctx.fromProgram().trim();
    next =
        from.isEmpty()
            ? ScreenId.COADM01
            : Arrays.stream(ScreenId.values())
                .filter(s -> s.program().equals(from))
                .findFirst()
                .orElse(ScreenId.COADM01);
  }

  <T> ScreenResponse<T> toResponse(
      ScreenId me, T data, ConversationContext ctx, UseradminState state) {
    ConversationContext out;
    if (next == null) {
      out = ctx.reenter();
    } else {
      out = ctx.transferTo(me.transactionId(), me.program(), next.transactionId(), next.program());
      if (!USERADMIN.contains(next)) {
        state.reset();
      }
    }
    ScreenResponse<T> r = ScreenResponse.of(me, data, out);
    if (next != null) {
      r = r.withNextScreen(next);
    }
    if (!message.isEmpty()) {
      r =
          r.withMessage(
              kind == Kind.INFO ? ScreenMessage.info(message) : ScreenMessage.error(message));
    }
    if (kind == Kind.ERROR && field != null) {
      r = r.withFieldError(new FieldError(field, message));
    }
    return r;
  }
}
