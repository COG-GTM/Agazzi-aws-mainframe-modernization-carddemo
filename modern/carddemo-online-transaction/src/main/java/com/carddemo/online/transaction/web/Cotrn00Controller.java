package com.carddemo.online.transaction.web;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cotrn00Screen;
import com.carddemo.online.transaction.service.Cotrn00cService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CT00 COTRN00C: BMS map COTRN0A as a REST resource (openapi.yaml cotrn00). */
@RestController
@RequestMapping("/api/v1/screens/cotrn00")
public class Cotrn00Controller {

  private final Cotrn00cService service;

  public Cotrn00Controller(Cotrn00cService service) {
    this.service = service;
  }

  @GetMapping
  public ScreenResponse<Cotrn00Screen.Output> get(
      HttpServletRequest request, HttpServletResponse response) {
    return ScreenSupport.firstSend(request, response, ScreenId.COTRN00, service::run);
  }

  @PostMapping
  public ScreenResponse<Cotrn00Screen.Output> post(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestBody ScreenRequest<Cotrn00Screen.Input> body) {
    return ScreenSupport.receive(
        request,
        response,
        ScreenId.COTRN00,
        service::run,
        Cotrn00Screen.Input.class,
        body.action(),
        body.fields(),
        java.util.Set.of());
  }
}
