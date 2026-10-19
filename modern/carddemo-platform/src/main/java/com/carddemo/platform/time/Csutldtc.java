package com.carddemo.platform.time;

import java.util.Objects;

/**
 * Port of the {@code CSUTLDTC} subroutine: validates a 10-byte date against a 10-byte {@code
 * CEEDAYS} picture and builds the 80-byte {@code LS-RESULT} message, byte for byte as the COBOL
 * does (including the halfword {@code VSTRING} length prefix that the program moves into {@code
 * WS-DATE}). {@code RETURN-CODE} is the condition severity.
 */
public final class Csutldtc {

  public static final int FIELD_LENGTH = 10;
  public static final int RESULT_LENGTH = 80;

  /** {@code LS-RESULT} plus {@code RETURN-CODE}, with the decoded {@code CEEDAYS} outcome. */
  public record Result(String message, int returnCode, CeeDays.Outcome outcome) {

    /** {@code WS-SEVERITY} (positions 1-4), e.g. {@code 0000} or {@code 0003}. */
    public String severityCode() {
      return message.substring(0, 4);
    }

    /** {@code WS-MSG-NO} (positions 16-19), e.g. {@code 2508}. */
    public String messageCode() {
      return message.substring(15, 19);
    }

    /** {@code WS-RESULT} (positions 21-35), e.g. {@code Date is valid }. */
    public String resultText() {
      return message.substring(20, 35);
    }

    public boolean valid() {
      return returnCode == 0;
    }
  }

  private final CeeDays ceeDays;

  public Csutldtc(CeeDays ceeDays) {
    this.ceeDays = Objects.requireNonNull(ceeDays, "ceeDays");
  }

  public Result call(String date, String format) {
    String lsDate = CobolDateFormatter.pad(Objects.requireNonNull(date, "date"), FIELD_LENGTH);
    String lsFormat =
        CobolDateFormatter.pad(Objects.requireNonNull(format, "format"), FIELD_LENGTH);
    CeeDays.Outcome outcome = ceeDays.toLillian(lsDate, lsFormat);
    CeeDays.Feedback feedback = outcome.feedback();
    // MOVE WS-DATE-TO-TEST TO WS-DATE: halfword length X'000A' followed by 8 date bytes.
    String wsDate = "\u0000" + (char) FIELD_LENGTH + lsDate.substring(0, FIELD_LENGTH - 2);
    String message =
        String.format("%04d", feedback.severity())
            + "Mesg Code: "
            + String.format("%04d", feedback.messageNumber())
            + " "
            + resultText(feedback)
            + " "
            + "TstDate: "
            + wsDate
            + " "
            + "Mask used:"
            + lsFormat
            + " "
            + "   ";
    return new Result(message, feedback.severity(), outcome);
  }

  /** {@code WS-RESULT} text of the {@code EVALUATE TRUE} on the feedback token (15 bytes). */
  public static String resultText(CeeDays.Feedback feedback) {
    String text =
        switch (feedback) {
          case VALID -> "Date is valid";
          case INSUFFICIENT_DATA -> "Insufficient";
          case BAD_DATE_VALUE -> "Datevalue error";
          case INVALID_ERA -> "Invalid Era";
          case UNSUPPORTED_RANGE -> "Unsupp. Range";
          case INVALID_MONTH -> "Invalid month";
          case BAD_PIC_STRING -> "Bad Pic String";
          case NON_NUMERIC_DATA -> "Nonnumeric data";
          case YEAR_IN_ERA_ZERO -> "YearInEra is 0";
        };
    return CobolDateFormatter.pad(text, 15);
  }
}
