package com.carddemo.online.account.service;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenResponse;

/**
 * Result of one program invocation: the response, the COMMAREA after {@code RETURN}/{@code XCTL},
 * and the program-specific COMMAREA ({@code null} after {@code XCTL}: the pseudo-conversation of
 * this transaction ends).
 *
 * @param <T> output DTO of the map
 * @param <S> program state ({@code WS-THIS-PROGCOMMAREA})
 */
public record ScreenOutcome<T, S>(
    ScreenResponse<T> response, ConversationContext context, S programState) {}
