package com.carddemo.ext.trantype.online;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CotrtliScreen;
import com.carddemo.contracts.screen.map.CotrtupScreen;
import com.carddemo.ext.trantype.TrantypeEnabled;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OpenAPI {@code /api/v1/screens/cotrtli} and {@code /api/v1/screens/cotrtup} (X2). */
@RestController
@TrantypeEnabled
@RequestMapping("/api/v1/screens")
public class TrantypeScreenController {

  static final String CTLI_STATE = "carddemo.x2.ctli";
  static final String CTTU_STATE = "carddemo.x2.cttu";

  private final CtliService ctli;
  private final CttuService cttu;

  private final TrantypeConversations conversations;

  public TrantypeScreenController(
      CtliService ctli, CttuService cttu, TrantypeConversations conversations) {
    this.ctli = ctli;
    this.cttu = cttu;
    this.conversations = conversations;
  }

  @GetMapping("/cotrtli")
  public ScreenResponse<CotrtliScreen.Output> ctliFirstSend(
      HttpServletRequest request, Principal user) {
    CtliState s = ctliState(request);
    ScreenResponse<CotrtliScreen.Output> r = ctli.firstSend(s, name(user));
    conversations.save(request, CTLI_STATE, s);
    return r;
  }

  @PostMapping("/cotrtli")
  public ScreenResponse<CotrtliScreen.Output> ctliReceive(
      @RequestBody ScreenRequest<CtliInput> body, HttpServletRequest request, Principal user) {
    CtliState s = ctliState(request);
    ScreenResponse<CotrtliScreen.Output> r =
        ctli.receive(body.action(), body.fields(), s, name(user));
    conversations.save(request, CTLI_STATE, s);
    if (r.nextScreen() == ScreenId.COTRTUP) {
      CttuState u = cttuState(request);
      u.enteredFrom(CtliService.TRANID, CtliService.PGM);
      conversations.save(request, CTTU_STATE, u);
    }
    return r;
  }

  @GetMapping("/cotrtup")
  public ScreenResponse<CotrtupScreen.Output> cttuFirstSend(
      HttpServletRequest request, Principal user) {
    CttuState s = cttuState(request);
    if (s.entered) {
      s.enteredFrom(ScreenSupport.ADMIN_TRANID, ScreenSupport.ADMIN_PGM);
    }
    ScreenResponse<CotrtupScreen.Output> r = cttu.firstSend(s, name(user));
    conversations.save(request, CTTU_STATE, s);
    return r;
  }

  @PostMapping("/cotrtup")
  public ScreenResponse<CotrtupScreen.Output> cttuReceive(
      @RequestBody ScreenRequest<CotrtupScreen.Input> body,
      HttpServletRequest request,
      Principal user) {
    CttuState s = cttuState(request);
    ScreenResponse<CotrtupScreen.Output> r =
        cttu.receive(body.action(), body.fields(), s, name(user));
    if (r.nextScreen() != null) {
      s.enteredFrom(null, null);
      if (r.nextScreen() == ScreenId.COTRTLI) {
        CtliState l = ctliState(request);
        l.entered = false;
        conversations.save(request, CTLI_STATE, l);
      }
    }
    conversations.save(request, CTTU_STATE, s);
    return r;
  }

  private static String name(Principal p) {
    return p == null ? "" : p.getName();
  }

  private CtliState ctliState(HttpServletRequest request) {
    return conversations.get(request, CTLI_STATE, CtliState::new);
  }

  private CttuState cttuState(HttpServletRequest request) {
    return conversations.get(request, CTTU_STATE, CttuState::new);
  }
}
