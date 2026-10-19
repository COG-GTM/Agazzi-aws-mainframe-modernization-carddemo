package com.carddemo.online.transaction.web;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.map.Corpt00Screen;
import com.carddemo.online.transaction.service.Corpt00cService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CR00 CORPT00C: BMS map CORPT0A as a REST resource (openapi.yaml corpt00). */
@RestController
@RequestMapping("/api/v1/screens/corpt00")
public class Corpt00Controller {

  private final Corpt00cService service;

  public Corpt00Controller(Corpt00cService service) {
    this.service = service;
  }

  @GetMapping
  public Corpt00Body get(HttpServletRequest request, HttpServletResponse response) {
    return Corpt00Body.of(
        ScreenSupport.firstSend(request, response, ScreenId.CORPT00, service::run), request);
  }

  @PostMapping
  public Corpt00Body post(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestBody ScreenRequest<Corpt00Screen.Input> body) {
    return Corpt00Body.of(
        ScreenSupport.receive(
            request,
            response,
            ScreenId.CORPT00,
            service::run,
            Corpt00Screen.Input.class,
            body.action(),
            body.fields(),
            Corpt00cService.NUM_FIELDS),
        request);
  }
}
