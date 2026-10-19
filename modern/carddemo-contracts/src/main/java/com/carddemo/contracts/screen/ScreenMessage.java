package com.carddemo.contracts.screen;

/**
 * A message shown on the screen (ERRMSG line or info line), text identical to the COBOL literal.
 */
public record ScreenMessage(Severity severity, String text) {

  /** Message severity: ERROR maps to the red ERRMSG attribute of the BMS maps. */
  public enum Severity {
    INFO,
    ERROR
  }

  public static ScreenMessage info(String text) {
    return new ScreenMessage(Severity.INFO, text);
  }

  public static ScreenMessage error(String text) {
    return new ScreenMessage(Severity.ERROR, text);
  }
}
