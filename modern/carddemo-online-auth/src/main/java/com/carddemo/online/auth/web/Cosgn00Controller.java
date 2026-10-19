package com.carddemo.online.auth.web;

import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cosgn00Screen;
import com.carddemo.online.auth.service.Cosgn00cService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CC00 sign-on resource ({@code /api/v1/screens/cosgn00}, public). */
@RestController
@RequestMapping(path = "/api/v1/screens/cosgn00", produces = "application/json")
public class Cosgn00Controller {

  private final Cosgn00cService service;
  private final ScreenSession session;

  public Cosgn00Controller(Cosgn00cService service, ScreenSession session) {
    this.service = service;
    this.session = session;
  }

  @GetMapping
  public ScreenResponse<Cosgn00Screen.Output> get(
      HttpServletRequest request, HttpServletResponse response) {
    return session.apply(service.firstSend(), request, response);
  }

  @PostMapping(consumes = "application/json")
  public ScreenResponse<Cosgn00Screen.Output> post(
      @RequestBody ScreenRequest<Cosgn00Screen.Input> body,
      HttpServletRequest request,
      HttpServletResponse response) {
    return session.apply(service.receive(body.action(), body.fields()), request, response);
  }
}
