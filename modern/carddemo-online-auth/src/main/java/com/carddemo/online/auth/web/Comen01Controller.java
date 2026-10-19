package com.carddemo.online.auth.web;

import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Comen01Screen;
import com.carddemo.online.auth.service.Comen01cService;
import com.carddemo.online.auth.session.ConversationStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CM00 main menu resource ({@code /api/v1/screens/comen01}, signed-on users). */
@RestController
@RequestMapping(path = "/api/v1/screens/comen01", produces = "application/json")
public class Comen01Controller {

  private final Comen01cService service;
  private final ConversationStore conversations;
  private final ScreenSession session;

  public Comen01Controller(
      Comen01cService service, ConversationStore conversations, ScreenSession session) {
    this.service = service;
    this.conversations = conversations;
    this.session = session;
  }

  @GetMapping
  public ScreenResponse<Comen01Screen.Output> get(
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    return session.apply(
        service.firstSend(conversations.resolve(request, auth)), request, response);
  }

  @PostMapping(consumes = "application/json")
  public ScreenResponse<Comen01Screen.Output> post(
      @RequestBody ScreenRequest<Comen01Screen.Input> body,
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
