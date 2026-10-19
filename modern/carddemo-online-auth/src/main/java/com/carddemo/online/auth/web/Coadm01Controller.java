package com.carddemo.online.auth.web;

import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Coadm01Screen;
import com.carddemo.online.auth.service.Coadm01cService;
import com.carddemo.online.auth.session.ConversationStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CA00 admin menu resource ({@code /api/v1/screens/coadm01}, ADMIN only). */
@RestController
@RequestMapping(path = "/api/v1/screens/coadm01", produces = "application/json")
public class Coadm01Controller {

  private final Coadm01cService service;
  private final ConversationStore conversations;
  private final ScreenSession session;

  public Coadm01Controller(
      Coadm01cService service, ConversationStore conversations, ScreenSession session) {
    this.service = service;
    this.conversations = conversations;
    this.session = session;
  }

  @GetMapping
  public ScreenResponse<Coadm01Screen.Output> get(
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    return session.apply(
        service.firstSend(conversations.resolve(request, auth)), request, response);
  }

  @PostMapping(consumes = "application/json")
  public ScreenResponse<Coadm01Screen.Output> post(
      @RequestBody ScreenRequest<Coadm01Screen.Input> body,
      Authentication auth,
      HttpServletRequest request,
      HttpServletResponse response) {
    String option = body.fields() == null ? null : body.fields().option();
    return session.apply(
        service.receive(conversations.resolve(request, auth), body.action(), option),
        request,
        response);
  }
}
