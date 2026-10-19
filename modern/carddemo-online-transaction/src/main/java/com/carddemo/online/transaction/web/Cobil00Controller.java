package com.carddemo.online.transaction.web;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cobil00Screen;
import com.carddemo.online.transaction.service.Cobil00cService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CB00 COBIL00C: BMS map COBIL0A as a REST resource (openapi.yaml cobil00). */
@RestController
@RequestMapping("/api/v1/screens/cobil00")
public class Cobil00Controller {

  private final Cobil00cService service;

  public Cobil00Controller(Cobil00cService service) {
    this.service = service;
  }

  @GetMapping
  public ScreenResponse<Cobil00Screen.Output> get(
      HttpServletRequest request, HttpServletResponse response) {
    return ScreenSupport.firstSend(request, response, ScreenId.COBIL00, service::run);
  }

  @PostMapping
  public ScreenResponse<Cobil00Screen.Output> post(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestBody ScreenRequest<Cobil00Screen.Input> body) {
    return ScreenSupport.receive(
        request,
        response,
        ScreenId.COBIL00,
        service::run,
        Cobil00Screen.Input.class,
        body.action(),
        body.fields(),
        java.util.Set.of());
  }
}
