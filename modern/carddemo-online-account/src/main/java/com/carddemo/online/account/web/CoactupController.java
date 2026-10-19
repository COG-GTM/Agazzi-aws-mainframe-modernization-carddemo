package com.carddemo.online.account.web;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CoactupScreen;
import com.carddemo.online.account.service.CoactupcService;
import com.carddemo.online.account.service.ScreenOutcome;
import com.carddemo.online.account.support.CommArea;
import com.carddemo.online.account.support.ConversationSession;
import com.carddemo.online.account.support.ScreenFields;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Screen resource of map CACTUPA (transaction CAUP). */
@RestController
@RequestMapping("/api/v1/screens/coactup")
public class CoactupController {

  /** Session attribute for the CAUP pseudo-conversation (WS-THIS-PROGCOMMAREA). */
  static final String STATE_ATTRIBUTE = "carddemo.o2.CAUP";

  private final CoactupcService service;

  public CoactupController(CoactupcService service) {
    this.service = service;
  }

  /** First send: the program is reached by XCTL with {@code CDEMO-PGM-ENTER}. */
  @GetMapping
  public ScreenResponse<CoactupScreen.Output> firstSend(HttpSession session, Principal principal) {
    CommArea arrival = CommArea.of(ConversationSession.current(session, principal));
    arrival.pgmContext = ProgramContext.ENTER;
    return run(session, principal, arrival.toContext(), null, Action.ENTER, null);
  }

  /**
   * Receive map. A field sent as {@code null} keeps the value shown on the screen (3270 FSET echo);
   * an empty string is an erased field.
   */
  @PostMapping
  public ScreenResponse<CoactupScreen.Output> receive(
      @RequestBody ScreenRequest<CoactupScreen.Input> request,
      HttpSession session,
      Principal principal) {
    ConversationContext current = ConversationSession.current(session, principal);
    ConversationContext commarea = null;
    CoactupcService.State state = null;
    CoactupScreen.Input fields = request.fields();
    if (session.getAttribute(STATE_ATTRIBUTE) instanceof CoactupcService.State st
        && st.contextAtReturn().equals(current)) {
      commarea = current;
      state = st;
      Map<String, String> merged = ScreenFields.toMap(fields);
      st.lastScreen()
          .forEach(
              (k, v) -> {
                if (merged.get(k) == null) {
                  merged.put(k, v);
                }
              });
      fields = ScreenFields.fromMap(CoactupScreen.Input.class, merged);
    }
    return run(session, principal, commarea, state, request.action(), fields);
  }

  private ScreenResponse<CoactupScreen.Output> run(
      HttpSession session,
      Principal principal,
      ConversationContext commarea,
      CoactupcService.State state,
      Action action,
      CoactupScreen.Input fields) {
    ScreenOutcome<CoactupScreen.Output, CoactupcService.State> out =
        service.process(
            commarea,
            state,
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
