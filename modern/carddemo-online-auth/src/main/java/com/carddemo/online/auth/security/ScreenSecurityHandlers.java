package com.carddemo.online.auth.security;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.online.auth.service.CicsMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 401/403 bodies as {@link ScreenResponse}: not signed on = {@code nextScreen COSGN00} (COMEN01C /
 * COADM01C with EIBCALEN = 0 return to the sign-on screen); a regular user on an admin screen gets
 * the COMEN01C literal 'No access - Admin Only option...' and is sent back to the main menu.
 */
public class ScreenSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper json;

  public ScreenSecurityHandlers(ObjectMapper json) {
    this.json = json;
  }

  static ScreenId screenOf(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return Arrays.stream(ScreenId.values())
        .filter(s -> uri.endsWith(s.path()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException {
    write(
        response,
        HttpServletResponse.SC_UNAUTHORIZED,
        ScreenResponse.of(screenOf(request), null, null).withNextScreen(ScreenId.COSGN00));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    write(
        response,
        HttpServletResponse.SC_FORBIDDEN,
        ScreenResponse.of(screenOf(request), null, null)
            .withMessage(ScreenMessage.error(CicsMessages.ADMIN_ONLY))
            .withNextScreen(ScreenId.COMEN01));
  }

  private void write(HttpServletResponse response, int status, ScreenResponse<?> body)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(response.getOutputStream(), body);
  }
}
