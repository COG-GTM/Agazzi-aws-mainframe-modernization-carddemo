package com.carddemo.online.auth.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * MAIN-PARA shared by COMEN01C and COADM01C (same structure): first send when PGM-CONTEXT is ENTER,
 * then EVALUATE EIBAID (ENTER = PROCESS-ENTER-KEY, PF3 = back to COSGN00C, other = invalid key).
 * Subclasses provide the option table and the program-specific PROCESS-ENTER-KEY tail.
 *
 * @param <T> map output DTO
 */
abstract class MenuProgram<T> {

  /** Mutable WS-MESSAGE / WS-ERR-FLG of one task. */
  static final class Task {
    String message;
    boolean error;
    ScreenMessage.Severity severity = ScreenMessage.Severity.ERROR;
    String optiono;

    void error(String text) {
      error = true;
      message = text;
      severity = ScreenMessage.Severity.ERROR;
    }

    void info(String text) {
      message = text;
      severity = ScreenMessage.Severity.INFO;
    }
  }

  protected final BusinessClock clock;
  protected final ExtensionFlags extensions;
  private final ScreenId screen;
  private final String tranId;
  private final String program;
  private final List<MenuOption> options;

  MenuProgram(
      ScreenId screen, List<MenuOption> options, BusinessClock clock, ExtensionFlags extensions) {
    this.screen = screen;
    this.tranId = screen.transactionId();
    this.program = screen.program();
    this.options = List.copyOf(options);
    this.clock = clock;
    this.extensions = extensions;
  }

  /** GET: COMMAREA present and NOT CDEMO-PGM-REENTER -> SET REENTER, LOW-VALUES, SEND-MENU. */
  public ScreenResult<T> firstSend(ConversationContext ctx) {
    return ScreenResult.keep(send(ctx.reenter(), new Task()));
  }

  /** POST: RECEIVE-MENU-SCREEN + EVALUATE EIBAID. */
  public ScreenResult<T> receive(ConversationContext ctx, Action action, String optioni) {
    if (ctx.programContext() != ProgramContext.REENTER) {
      return firstSend(ctx);
    }
    Task task = new Task();
    return switch (action) {
      case ENTER -> processEnterKey(ctx, optioni, task);
      case PF3 ->
          // MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM, RETURN-TO-SIGNON-SCREEN (XCTL without COMMAREA)
          ScreenResult.signOff(
              ScreenResponse.<T>of(screen, null, null).withNextScreen(ScreenId.COSGN00));
      default -> {
        task.error(CicsMessages.INVALID_KEY);
        yield ScreenResult.keep(send(ctx, task));
      }
    };
  }

  // PROCESS-ENTER-KEY
  private ScreenResult<T> processEnterKey(ConversationContext ctx, String optioni, Task task) {
    OptionInput opt = OptionInput.parse(optioni);
    task.optiono = opt.numeric() ? opt.optiono() : ScreenText.out(ScreenText.picX(optioni, 2));
    if (!opt.validFor(options.size())) {
      task.error(CicsMessages.INVALID_OPTION);
      return ScreenResult.keep(send(ctx, task));
    }
    return selected(ctx, options.get(opt.value() - 1), task);
  }

  /** Program-specific part of PROCESS-ENTER-KEY once WS-OPTION is valid. */
  protected abstract ScreenResult<T> selected(
      ConversationContext ctx, MenuOption option, Task task);

  /** {@code MOVE WS-TRANID TO CDEMO-FROM-TRANID ... XCTL PROGRAM(option)}. */
  protected ScreenResult<T> xctl(ConversationContext ctx, MenuOption option) {
    ScreenId target = option.screen().orElseThrow();
    ConversationContext next =
        ctx.transferTo(tranId, program, target.transactionId(), target.program());
    return ScreenResult.keep(ScreenResponse.<T>of(screen, null, next).withNextScreen(target));
  }

  // SEND-MENU-SCREEN: POPULATE-HEADER-INFO + BUILD-MENU-OPTIONS + WS-MESSAGE to ERRMSGO
  protected ScreenResponse<T> send(ConversationContext ctx, Task task) {
    ScreenHeader header = ScreenHeader.of(tranId, program, clock);
    List<String> texts = new ArrayList<>();
    for (MenuOption o : options) {
      // options of modules that are not installed are not shown (carddemo.extensions.*)
      texts.add(extensions.installed(o) ? ScreenText.out(o.menuText()) : null);
    }
    T data = output(header, texts, task.optiono, ScreenText.errmsg(task.message));
    ScreenResponse<T> r = ScreenResponse.of(screen, data, ctx);
    if (task.message != null) {
      r = r.withMessage(new ScreenMessage(task.severity, task.message));
    }
    return r;
  }

  /** Builds the map output; {@code options.get(i)} goes to OPTN00(i+1), {@code null} = blank. */
  protected abstract T output(
      ScreenHeader header, List<String> options, String optiono, String errmsg);

  static String slot(List<String> options, int index) {
    return index < options.size() ? options.get(index) : null;
  }
}
