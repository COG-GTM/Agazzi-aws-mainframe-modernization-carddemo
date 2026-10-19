package com.carddemo.online.auth.service;

import com.carddemo.contracts.port.UserCredentialService.AuthenticatedUser;
import com.carddemo.contracts.screen.ScreenResponse;

/**
 * Result of a screen interaction plus what has to happen to the HTTP session: keep it, sign on
 * ({@code user} authenticated, new session id) or sign off (session invalidated, ADR-002).
 */
public record ScreenResult<T>(ScreenResponse<T> response, Effect effect, AuthenticatedUser user) {

  /** Session side effect of the interaction. */
  public enum Effect {
    KEEP,
    SIGN_ON,
    SIGN_OFF
  }

  public static <T> ScreenResult<T> keep(ScreenResponse<T> response) {
    return new ScreenResult<>(response, Effect.KEEP, null);
  }

  public static <T> ScreenResult<T> signOff(ScreenResponse<T> response) {
    return new ScreenResult<>(response, Effect.SIGN_OFF, null);
  }

  public static <T> ScreenResult<T> signOn(ScreenResponse<T> response, AuthenticatedUser user) {
    return new ScreenResult<>(response, Effect.SIGN_ON, user);
  }
}
