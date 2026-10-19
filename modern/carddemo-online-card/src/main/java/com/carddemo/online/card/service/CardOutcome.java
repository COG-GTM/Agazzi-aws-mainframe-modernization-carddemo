package com.carddemo.online.card.service;

import com.carddemo.contracts.screen.ScreenResponse;

/** Result of one pseudo-conversational step: the screen response and the new program state. */
public record CardOutcome<T, S>(ScreenResponse<T> response, S state) {}
