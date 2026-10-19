package com.carddemo.online.transaction.web;

import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Corpt00Screen;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code Corpt00Response} of openapi.yaml: the screen response plus the TRANREPT execution
 * submitted by this interaction ({@code reportExecutionId}, also in the header and Location).
 */
public record Corpt00Body(
    @JsonUnwrapped ScreenResponse<Corpt00Screen.Output> response, Long reportExecutionId) {

  static Corpt00Body of(ScreenResponse<Corpt00Screen.Output> response, HttpServletRequest request) {
    return new Corpt00Body(
        response, request.getAttribute(ScreenSupport.REPORT_HEADER) instanceof Long id ? id : null);
  }
}
