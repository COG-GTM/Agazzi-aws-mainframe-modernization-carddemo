package com.carddemo.online.auth.service;

/**
 * PROCESS-ENTER-KEY option parsing of COMEN01C/COADM01C: OPTIONI (PIC X(02)) is right-trimmed,
 * moved to WS-OPTION-X (PIC X(02) JUST RIGHT), spaces become zeros ({@code INSPECT ... REPLACING
 * ALL ' ' BY '0'}) and the result is moved to WS-OPTION (PIC 9(02)).
 *
 * @param optionX WS-OPTION-X after the INSPECT
 * @param value WS-OPTION, or {@code -1} when {@code WS-OPTION IS NOT NUMERIC}
 */
public record OptionInput(String optionX, int value) {

  public static OptionInput parse(String optioni) {
    String field = ScreenText.picX(optioni == null ? "" : optioni, 2);
    // PERFORM VARYING WS-IDX FROM 2 BY -1 UNTIL OPTIONI(WS-IDX:1) NOT = SPACES OR WS-IDX = 1
    int idx = 2;
    while (idx > 1 && field.charAt(idx - 1) == ' ') {
      idx--;
    }
    String sub = field.substring(0, idx);
    String justRight = " ".repeat(2 - sub.length()) + sub;
    String optionX = justRight.replace(' ', '0');
    boolean numeric = optionX.chars().allMatch(c -> c >= '0' && c <= '9');
    return new OptionInput(optionX, numeric ? Integer.parseInt(optionX) : -1);
  }

  public boolean numeric() {
    return value >= 0;
  }

  /** {@code WS-OPTION IS NOT NUMERIC OR WS-OPTION > count OR WS-OPTION = ZEROS} negated. */
  public boolean validFor(int count) {
    return numeric() && value > 0 && value <= count;
  }

  /** {@code MOVE WS-OPTION TO OPTIONO}. */
  public String optiono() {
    return optionX;
  }
}
