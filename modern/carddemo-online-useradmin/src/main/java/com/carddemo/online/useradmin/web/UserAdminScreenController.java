package com.carddemo.online.useradmin.web;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenRequest;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cousr00Screen;
import com.carddemo.contracts.screen.map.Cousr01Screen;
import com.carddemo.contracts.screen.map.Cousr02Screen;
import com.carddemo.contracts.screen.map.Cousr03Screen;
import com.carddemo.online.useradmin.service.UserAddService;
import com.carddemo.online.useradmin.service.UserDeleteService;
import com.carddemo.online.useradmin.service.UserListService;
import com.carddemo.online.useradmin.service.UserUpdateService;
import com.carddemo.online.useradmin.session.UseradminSession;
import com.carddemo.online.useradmin.session.UseradminSession.Conversation;
import com.carddemo.online.useradmin.session.UseradminState;
import jakarta.servlet.http.HttpSession;
import java.util.function.BiFunction;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BMS maps COUSR0A-COUSR3A: {@code GET} = first send, {@code POST} = AID key + fields. */
@RestController
@RequestMapping("/api/v1/screens")
public class UserAdminScreenController {

  private final UseradminSession sessions;
  private final UserListService list;
  private final UserAddService add;
  private final UserUpdateService update;
  private final UserDeleteService delete;

  public UserAdminScreenController(
      UseradminSession sessions,
      UserListService list,
      UserAddService add,
      UserUpdateService update,
      UserDeleteService delete) {
    this.sessions = sessions;
    this.list = list;
    this.add = add;
    this.update = update;
    this.delete = delete;
  }

  @GetMapping("/cousr00")
  public ScreenResponse<Cousr00Screen.Output> getCousr00(HttpSession session, Authentication auth) {
    return run(session, auth, list::send);
  }

  @PostMapping("/cousr00")
  public ScreenResponse<Cousr00Screen.Output> postCousr00(
      @RequestBody ScreenRequest<Cousr00Screen.Input> request,
      HttpSession session,
      Authentication auth) {
    return run(session, auth, (c, s) -> list.receive(request.action(), request.fields(), c, s));
  }

  @GetMapping("/cousr01")
  public ScreenResponse<Cousr01Screen.Output> getCousr01(HttpSession session, Authentication auth) {
    return run(session, auth, add::send);
  }

  @PostMapping("/cousr01")
  public ScreenResponse<Cousr01Screen.Output> postCousr01(
      @RequestBody ScreenRequest<Cousr01Screen.Input> request,
      HttpSession session,
      Authentication auth) {
    return run(session, auth, (c, s) -> add.receive(request.action(), request.fields(), c, s));
  }

  @GetMapping("/cousr02")
  public ScreenResponse<Cousr02Screen.Output> getCousr02(HttpSession session, Authentication auth) {
    return run(session, auth, update::send);
  }

  @PostMapping("/cousr02")
  public ScreenResponse<Cousr02Screen.Output> postCousr02(
      @RequestBody ScreenRequest<Cousr02Screen.Input> request,
      HttpSession session,
      Authentication auth) {
    return run(session, auth, (c, s) -> update.receive(request.action(), request.fields(), c, s));
  }

  @GetMapping("/cousr03")
  public ScreenResponse<Cousr03Screen.Output> getCousr03(HttpSession session, Authentication auth) {
    return run(session, auth, delete::send);
  }

  @PostMapping("/cousr03")
  public ScreenResponse<Cousr03Screen.Output> postCousr03(
      @RequestBody ScreenRequest<Cousr03Screen.Input> request,
      HttpSession session,
      Authentication auth) {
    return run(session, auth, (c, s) -> delete.receive(request.action(), request.fields(), c, s));
  }

  private <T> ScreenResponse<T> run(
      HttpSession session,
      Authentication auth,
      BiFunction<ConversationContext, UseradminState, ScreenResponse<T>> program) {
    Conversation conv = sessions.open(session, auth);
    ScreenResponse<T> response = program.apply(conv.context(), conv.state());
    sessions.save(session, response.context(), conv.state());
    return response;
  }
}
