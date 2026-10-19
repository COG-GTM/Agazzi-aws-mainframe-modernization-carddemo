package com.carddemo.online.transaction.web;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cotrn02Screen;
import com.carddemo.online.transaction.service.Cotrn02cService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CT02 COTRN02C: BMS map COTRN2A as a REST resource (openapi.yaml cotrn02). */
@RestController
@RequestMapping("/api/v1/screens/cotrn02")
public class Cotrn02Controller {

  private final Cotrn02cService service;

  public Cotrn02Controller(Cotrn02cService service) {
    this.service = service;
  }

  @GetMapping
  public ScreenResponse<Cotrn02Screen.Output> get(
      HttpServletRequest request, HttpServletResponse response) {
    return ScreenSupport.firstSend(request, response, ScreenId.COTRN02, service::run);
  }

  @PostMapping
  public ScreenResponse<Cotrn02Screen.Output> post(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestBody ScreenRequest<Cotrn02Screen.Input> body) {
    return ScreenSupport.receive(
        request,
        response,
        ScreenId.COTRN02,
        service::run,
        Cotrn02Screen.Input.class,
        body.action(),
        body.fields(),
        java.util.Set.of());
  }
}
