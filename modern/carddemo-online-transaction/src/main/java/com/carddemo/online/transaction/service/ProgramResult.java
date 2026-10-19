package com.carddemo.online.transaction.service;

import com.carddemo.contracts.screen.ScreenResponse;

/**
 * Result of one pseudo-conversational task: the screen response (with the COMMAREA in {@code
 * context}), the program area to keep in session and, for CR00, the report job submitted.
 */
public record ProgramResult<O>(
    ScreenResponse<O> response, CommareaExtension extension, Long reportExecutionId) {

  public ProgramResult(ScreenResponse<O> response, CommareaExtension extension) {
    this(response, extension, null);
  }
}
