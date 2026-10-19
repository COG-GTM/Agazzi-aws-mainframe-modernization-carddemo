package com.carddemo.contracts.screen;

import java.util.Objects;

/**
 * Terminal input for a screen: the AID key plus the unprotected map fields ({@code
 * <Map>Screen.Input}).
 *
 * @param <T> input DTO of the map
 */
public record ScreenRequest<T>(Action action, T fields) {
  public ScreenRequest {
    Objects.requireNonNull(action, "action");
  }
}
