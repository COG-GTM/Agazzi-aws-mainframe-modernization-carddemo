package com.carddemo.online.card.web;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CocrdliScreen;
import com.carddemo.contracts.screen.map.CocrdslScreen;
import com.carddemo.contracts.screen.map.CocrdupScreen;
import com.carddemo.online.card.service.CardDetailService;
import com.carddemo.online.card.service.CardListFields;
import com.carddemo.online.card.service.CardListService;
import com.carddemo.online.card.service.CardOutcome;
import com.carddemo.online.card.service.CardUpdateFields;
import com.carddemo.online.card.service.CardUpdateService;
import com.carddemo.online.card.session.CardConversation;
import com.carddemo.online.card.session.CardListState;
import com.carddemo.online.card.session.CardUpdateState;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BMS maps CCRDLIA, CCRDSLA, CCRDUPA as REST resources (one GET/POST pair per map). A POST on a
 * session that has not been sent a map yet first performs the SEND of the map (GET), as CICS always
 * sends the map before receiving it.
 */
@RestController
@RequestMapping("/api/v1/screens")
public class CardScreensController {

  private final CardConversation conversation;
  private final CardListService list;
  private final CardDetailService detail;
  private final CardUpdateService update;

  public CardScreensController(
      CardConversation conversation,
      CardListService list,
      CardDetailService detail,
      CardUpdateService update) {
    this.conversation = conversation;
    this.list = list;
    this.detail = detail;
    this.update = update;
  }

  @GetMapping("/cocrdli")
  public ScreenResponse<CocrdliScreen.Output> cocrdliSend(HttpSession session, Principal user) {
    return cocrdli(session, user, Action.ENTER, null, true);
  }

  @PostMapping("/cocrdli")
  public ScreenResponse<CocrdliScreen.Output> cocrdliReceive(
      @RequestBody ScreenRequest<CardListFields> request, HttpSession session, Principal user) {
    if (!conversation.hasContext(session, user)) {
      cocrdli(session, user, Action.ENTER, null, true);
    }
    return cocrdli(session, user, request.action(), request.fields(), false);
  }

  @GetMapping("/cocrdsl")
  public ScreenResponse<CocrdslScreen.Output> cocrdslSend(HttpSession session, Principal user) {
    return cocrdsl(session, user, Action.ENTER, null, true);
  }

  @PostMapping("/cocrdsl")
  public ScreenResponse<CocrdslScreen.Output> cocrdslReceive(
      @RequestBody ScreenRequest<CocrdslScreen.Input> request,
      HttpSession session,
      Principal user) {
    if (!conversation.hasContext(session, user)) {
      cocrdsl(session, user, Action.ENTER, null, true);
    }
    return cocrdsl(session, user, request.action(), request.fields(), false);
  }

  @GetMapping("/cocrdup")
  public ScreenResponse<CocrdupScreen.Output> cocrdupSend(HttpSession session, Principal user) {
    return cocrdup(session, user, Action.ENTER, null, true);
  }

  @PostMapping("/cocrdup")
  public ScreenResponse<CocrdupScreen.Output> cocrdupReceive(
      @RequestBody ScreenRequest<CardUpdateFields> request, HttpSession session, Principal user) {
    if (!conversation.hasContext(session, user)) {
      cocrdup(session, user, Action.ENTER, null, true);
    }
    return cocrdup(session, user, request.action(), request.fields(), false);
  }

  private ScreenResponse<CocrdliScreen.Output> cocrdli(
      HttpSession session, Principal user, Action action, CardListFields f, boolean first) {
    ConversationContext ctx = conversation.context(session, user);
    CardOutcome<CocrdliScreen.Output, CardListState> out =
        list.process(
            ctx,
            conversation.listState(session),
            conversation.ownedAccount(session, ctx),
            action,
            f,
            first);
    conversation.storeListState(session, out.state());
    conversation.store(session, out.response().context());
    return out.response();
  }

  private ScreenResponse<CocrdslScreen.Output> cocrdsl(
      HttpSession session, Principal user, Action action, CocrdslScreen.Input f, boolean first) {
    ConversationContext ctx = conversation.context(session, user);
    CardOutcome<CocrdslScreen.Output, Void> out =
        detail.process(ctx, conversation.ownedAccount(session, ctx), action, f, first);
    conversation.store(session, out.response().context());
    return out.response();
  }

  private ScreenResponse<CocrdupScreen.Output> cocrdup(
      HttpSession session, Principal user, Action action, CardUpdateFields f, boolean first) {
    ConversationContext ctx = conversation.context(session, user);
    CardOutcome<CocrdupScreen.Output, CardUpdateState> out =
        update.process(
            ctx,
            conversation.updateState(session),
            conversation.ownedAccount(session, ctx),
            action,
            f,
            first);
    conversation.storeUpdateState(session, out.state());
    conversation.store(session, out.response().context());
    return out.response();
  }
}
