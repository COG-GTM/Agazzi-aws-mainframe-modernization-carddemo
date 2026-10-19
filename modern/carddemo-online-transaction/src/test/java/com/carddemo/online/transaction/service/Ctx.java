package com.carddemo.online.transaction.service;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;

/** Test helpers for COMMAREAs and responses. */
final class Ctx {

  private Ctx() {}

  /** COMMAREA as passed by the user menu XCTL to {@code program}. */
  static ConversationContext fromMenu(String tran, String program) {
    return ConversationContext.signedOn("USER0001", UserType.USER)
        .transferTo("CM00", "COMEN01C", tran, program);
  }

  static String message(ScreenResponse<?> r) {
    return r.messages().isEmpty() ? null : r.messages().get(0).text();
  }

  static boolean green(ScreenResponse<?> r) {
    return !r.messages().isEmpty() && r.messages().get(0).severity() == ScreenMessage.Severity.INFO;
  }
}
