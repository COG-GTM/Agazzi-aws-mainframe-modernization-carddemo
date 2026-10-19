package com.carddemo.contracts.screen;

import java.util.List;

/**
 * Response of every screen resource: the map output, messages, the XCTL target ({@code nextScreen},
 * {@code null} = stay on the same screen), field errors and the updated conversation context.
 *
 * @param <T> output DTO of the map
 */
public record ScreenResponse<T>(
    ScreenId screen,
    T data,
    List<ScreenMessage> messages,
    ScreenId nextScreen,
    List<FieldError> fieldErrors,
    ConversationContext context) {

  public ScreenResponse {
    messages = messages == null ? List.of() : List.copyOf(messages);
    fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
  }

  public static <T> ScreenResponse<T> of(ScreenId screen, T data, ConversationContext context) {
    return new ScreenResponse<>(screen, data, List.of(), null, List.of(), context);
  }

  public ScreenResponse<T> withMessage(ScreenMessage message) {
    var all = new java.util.ArrayList<>(messages);
    all.add(message);
    return new ScreenResponse<>(screen, data, all, nextScreen, fieldErrors, context);
  }

  public ScreenResponse<T> withNextScreen(ScreenId next) {
    return new ScreenResponse<>(screen, data, messages, next, fieldErrors, context);
  }

  public ScreenResponse<T> withFieldError(FieldError error) {
    var all = new java.util.ArrayList<>(fieldErrors);
    all.add(error);
    return new ScreenResponse<>(screen, data, messages, nextScreen, all, context);
  }
}
