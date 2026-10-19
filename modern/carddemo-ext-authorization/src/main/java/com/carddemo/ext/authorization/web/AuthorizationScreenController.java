package com.carddemo.ext.authorization.web;

import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Copau00Screen;
import com.carddemo.contracts.screen.map.Copau01Screen;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CPVS (COPAUS0C, map COPAU0A) and CPVD (COPAUS1C, map COPAU1A) screen endpoints. */
@RestController
@RequestMapping("/api/v1/screens")
@ConditionalOnAuthorizationExtension
public class AuthorizationScreenController {

  private final CpvsScreenService cpvs;
  private final CpvdScreenService cpvd;

  public AuthorizationScreenController(CpvsScreenService cpvs, CpvdScreenService cpvd) {
    this.cpvs = cpvs;
    this.cpvd = cpvd;
  }

  @GetMapping("/copau00")
  public ScreenResponse<Copau00Screen.Output> firstCopau00(Principal p) {
    return cpvs.first(user(p));
  }

  @PostMapping("/copau00")
  public ScreenResponse<Copau00Screen.Output> receiveCopau00(
      @RequestBody ScreenRequest<Copau00Screen.Input> req, Principal p) {
    return cpvs.receive(user(p), req.action(), req.fields());
  }

  @GetMapping("/copau01")
  public ScreenResponse<Copau01Screen.Output> firstCopau01(Principal p) {
    return cpvd.first(user(p));
  }

  @PostMapping("/copau01")
  public ScreenResponse<Copau01Screen.Output> receiveCopau01(
      @RequestBody ScreenRequest<Copau01Screen.Input> req, Principal p) {
    return cpvd.receive(user(p), req.action());
  }

  private static String user(Principal p) {
    return p == null ? "anonymous" : p.getName();
  }
}
