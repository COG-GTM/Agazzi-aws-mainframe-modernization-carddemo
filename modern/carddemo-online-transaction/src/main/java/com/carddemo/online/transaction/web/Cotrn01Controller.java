package com.carddemo.online.transaction.web;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cotrn01Screen;
import com.carddemo.online.transaction.service.Cotrn01cService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CT01 COTRN01C: BMS map COTRN1A as a REST resource (openapi.yaml cotrn01). */
@RestController
@RequestMapping("/api/v1/screens/cotrn01")
public class Cotrn01Controller {

  private final Cotrn01cService service;

  public Cotrn01Controller(Cotrn01cService service) {
    this.service = service;
  }

  @GetMapping
  public ScreenResponse<Cotrn01Screen.Output> get(
      HttpServletRequest request, HttpServletResponse response) {
    return ScreenSupport.firstSend(request, response, ScreenId.COTRN01, service::run);
  }

  @PostMapping
  public ScreenResponse<Cotrn01Screen.Output> post(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestBody ScreenRequest<Cotrn01Screen.Input> body) {
    return ScreenSupport.receive(
        request,
        response,
        ScreenId.COTRN01,
        service::run,
        Cotrn01Screen.Input.class,
        body.action(),
        body.fields(),
        java.util.Set.of());
  }
}
