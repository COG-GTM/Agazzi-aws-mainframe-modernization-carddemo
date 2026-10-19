package com.carddemo.online.transaction.web;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.online.transaction.service.BmsMaps;
import com.carddemo.online.transaction.service.CommareaExtension;
import com.carddemo.online.transaction.service.MapBuffer;
import com.carddemo.online.transaction.service.Navigation;
import com.carddemo.online.transaction.service.ProgramResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Set;

/** Pseudo-conversation plumbing shared by the O4 screen resources. */
final class ScreenSupport {

  /** Header carrying the TRANREPT execution submitted by CR00. */
  static final String REPORT_HEADER = "X-CardDemo-Report-Execution-Id";

  /** A COBOL program: COMMAREA + program area + AID + received map to the task result. */
  @FunctionalInterface
  interface Program<O> {
    ProgramResult<O> run(
        ConversationContext ctx, CommareaExtension ext, Action aid, Map<String, String> input);
  }

  private ScreenSupport() {}

  /** GET: first send of the map (CDEMO-PGM-ENTER). */
  static <O> ScreenResponse<O> firstSend(
      HttpServletRequest request, HttpServletResponse response, ScreenId screen, Program<O> p) {
    return handle(request, response, screen, p, null, null, true);
  }

  /** POST: RECEIVE MAP with the AID key. */
  static <I extends Record, O> ScreenResponse<O> receive(
      HttpServletRequest request,
      HttpServletResponse response,
      ScreenId screen,
      Program<O> p,
      Class<I> inputType,
      Action aid,
      I fields,
      Set<String> numFields) {
    return handle(
        request, response, screen, p, aid, BmsMaps.receive(inputType, fields, numFields), false);
  }

  private static <O> ScreenResponse<O> handle(
      HttpServletRequest request,
      HttpServletResponse response,
      ScreenId screen,
      Program<O> program,
      Action aid,
      Map<String, String> input,
      boolean first) {
    HttpSession session = request.getSession(true);
    ConversationContext ctx = ConversationStore.context(session);
    if (ctx == null) {
      // EIBCALEN = 0: MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM, RETURN-TO-PREV-SCREEN
      return MapBuffer.xctl(screen, ScreenId.COSGN00, null);
    }
    if (first) {
      ctx = Navigation.enter(ctx);
    }
    ProgramResult<O> result =
        program.run(ctx, ConversationStore.extension(session, ctx), aid, input);
    ConversationStore.save(session, result.response().context(), result.extension());
    if (result.reportExecutionId() != null) {
      session.setAttribute(ConversationStore.REPORT_ATTRIBUTE, result.reportExecutionId());
      response.setHeader(REPORT_HEADER, String.valueOf(result.reportExecutionId()));
      response.setHeader("Location", "/api/v1/jobs/" + result.reportExecutionId());
    }
    return result.response();
  }
}
