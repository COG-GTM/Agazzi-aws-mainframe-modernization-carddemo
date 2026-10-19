package com.carddemo.online.account.web;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CoactvwScreen;
import com.carddemo.online.account.service.CoactvwcService;
import com.carddemo.online.account.service.ScreenOutcome;
import com.carddemo.online.account.support.CommArea;
import com.carddemo.online.account.support.ConversationSession;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Screen resource of map CACTVWA (transaction CAVW). */
@RestController
@RequestMapping("/api/v1/screens/coactvw")
public class CoactvwController {

  /** Session attribute for the CAVW pseudo-conversation ({@code RETURN TRANSID(CAVW)}). */
  static final String STATE_ATTRIBUTE = "carddemo.o2.CAVW";

  private final CoactvwcService service;

  public CoactvwController(CoactvwcService service) {
    this.service = service;
  }

  /** First send: the program is reached by XCTL with {@code CDEMO-PGM-ENTER}. */
  @GetMapping
  public ScreenResponse<CoactvwScreen.Output> firstSend(HttpSession session, Principal principal) {
    ConversationContext current = ConversationSession.current(session, principal);
    CommArea arrival = CommArea.of(current);
    arrival.pgmContext = ProgramContext.ENTER;
    return run(session, principal, arrival.toContext(), Action.ENTER, null);
  }

  /** Receive map: AID key + unprotected fields. */
  @PostMapping
  public ScreenResponse<CoactvwScreen.Output> receive(
      @RequestBody ScreenRequest<CoactvwScreen.Input> request,
      HttpSession session,
      Principal principal) {
    ConversationContext current = ConversationSession.current(session, principal);
    CoactvwScreen.Input fields = request.fields();
    ConversationContext commarea = null;
    if (session.getAttribute(STATE_ATTRIBUTE) instanceof CoactvwcService.State st
        && st.contextAtReturn().equals(current)) {
      commarea = current;
      if (fields == null || fields.acctsid() == null) {
        // 3270 FSET: an untouched field is sent back with the value shown on the screen
        fields = new CoactvwScreen.Input(st.lastAcctsid());
      }
    }
    return run(session, principal, commarea, request.action(), fields);
  }

  private ScreenResponse<CoactvwScreen.Output> run(
      HttpSession session,
      Principal principal,
      ConversationContext commarea,
      Action action,
      CoactvwScreen.Input fields) {
    ScreenOutcome<CoactvwScreen.Output, CoactvwcService.State> out =
        service.process(
            commarea,
            principal == null ? null : principal.getName(),
            ConversationSession.userType(principal),
            action,
            fields);
    ConversationSession.store(session, out.context());
    if (out.programState() == null) {
      session.removeAttribute(STATE_ATTRIBUTE);
    } else {
      session.setAttribute(STATE_ATTRIBUTE, out.programState());
    }
    return out.response();
  }
}
