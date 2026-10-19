package com.carddemo.platform.time;

import com.carddemo.contracts.port.BusinessClock;
import java.util.Objects;

/**
 * Port of the {@code CSUTLDPY}/{@code CSUTLDWY} date-edit paragraphs ({@code EDIT-DATE-CCYYMMDD}
 * and {@code EDIT-DATE-OF-BIRTH}) used by the online programs. The checks, their order, the flags
 * and the message texts are those of the copybook; {@code CSUTLDTC} is called as the final calendar
 * check. Messages are only set when no earlier message is present ({@code WS-RETURN-MSG-OFF}) and
 * are truncated to {@code WS-RETURN-MSG} ({@code PIC X(75)}).
 */
public final class CcyymmddDateEdit {

  public static final int RETURN_MSG_LENGTH = 75;

  /** Per-component flag: {@code LOW-VALUES} (valid), {@code '0'} (not ok) or {@code 'B'}. */
  public enum Flag {
    VALID,
    NOT_OK,
    BLANK
  }

  /** Outcome of an edit: component flags, input-error switch and the return message. */
  public record Result(Flag year, Flag month, Flag day, boolean inputError, String returnMessage) {
    public boolean valid() {
      return !inputError && year == Flag.VALID && month == Flag.VALID && day == Flag.VALID;
    }
  }

  private final Csutldtc csutldtc;
  private final BusinessClock clock;

  public CcyymmddDateEdit(BusinessClock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.csutldtc = new Csutldtc(new CeeDays(clock));
  }

  private static final class State {
    Flag year = Flag.NOT_OK;
    Flag month = Flag.NOT_OK;
    Flag day = Flag.NOT_OK;
    boolean inputError;
    String message = "";
    final String name;

    State(String name) {
      this.name = name.strip();
    }

    void fail(String text) {
      inputError = true;
      if (message.isEmpty()) {
        message = text.length() > RETURN_MSG_LENGTH ? text.substring(0, RETURN_MSG_LENGTH) : text;
      }
    }

    Result result() {
      return new Result(year, month, day, inputError, message);
    }
  }

  /**
   * {@code EDIT-DATE-CCYYMMDD}.
   *
   * @param fieldName {@code WS-EDIT-VARIABLE-NAME}, used as message prefix
   * @param ccyymmdd {@code WS-EDIT-DATE-CCYYMMDD}; padded or truncated to 8 bytes; {@code \0}
   *     stands for {@code LOW-VALUES}
   */
  public Result editCcyymmdd(String fieldName, String ccyymmdd) {
    State s = new State(fieldName);
    String date = CobolDateFormatter.pad(Objects.requireNonNull(ccyymmdd, "ccyymmdd"), 8);
    String ccyy = date.substring(0, 4);
    String mm = date.substring(4, 6);
    String dd = date.substring(6, 8);
    int ccyyN = numeric(ccyy);
    int mmN = numeric(mm);
    int ddN = numeric(dd);

    // EDIT-YEAR-CCYY
    if (blank(ccyy)) {
      s.year = Flag.BLANK;
      s.fail(s.name + " : Year must be supplied.");
    } else if (ccyyN < 0) {
      s.fail(s.name + " must be 4 digit number.");
    } else if (ccyyN / 100 != 19 && ccyyN / 100 != 20) {
      s.fail(s.name + " : Century is not valid.");
    } else {
      s.year = Flag.VALID;
    }
    // EDIT-MONTH
    if (blank(mm)) {
      s.month = Flag.BLANK;
      s.fail(s.name + " : Month must be supplied.");
    } else if (mmN < 1 || mmN > 12) {
      s.fail(s.name + ": Month must be a number between 1 and 12.");
    } else {
      s.month = Flag.VALID;
    }
    // EDIT-DAY
    if (blank(dd)) {
      s.day = Flag.BLANK;
      s.fail(s.name + " : Day must be supplied.");
    } else if (ddN < 1 || ddN > 31) {
      s.fail(s.name + ":day must be a number between 1 and 31.");
    } else {
      s.day = Flag.VALID;
    }
    // EDIT-DAY-MONTH-YEAR
    boolean thirtyOneDayMonth =
        switch (mmN) {
          case 1, 3, 5, 7, 8, 10, 12 -> true;
          default -> false;
        };
    if (!thirtyOneDayMonth && ddN == 31) {
      s.day = Flag.NOT_OK;
      s.month = Flag.NOT_OK;
      s.fail(s.name + ":Cannot have 31 days in this month.");
      return s.result();
    }
    if (mmN == 2 && ddN == 30) {
      s.day = Flag.NOT_OK;
      s.month = Flag.NOT_OK;
      s.fail(s.name + ":Cannot have 30 days in this month.");
      return s.result();
    }
    if (mmN == 2 && ddN == 29) {
      int divisor = ccyyN >= 0 && ccyyN % 100 == 0 ? 400 : 4;
      if (ccyyN < 0 || ccyyN % divisor != 0) {
        s.day = Flag.NOT_OK;
        s.month = Flag.NOT_OK;
        s.year = Flag.NOT_OK;
        s.fail(s.name + ":Not a leap year.Cannot have 29 days in this month.");
        return s.result();
      }
    }
    if (s.year != Flag.VALID || s.month != Flag.VALID || s.day != Flag.VALID) {
      return s.result();
    }
    // EDIT-DATE-LE: CALL 'CSUTLDTC' USING WS-EDIT-DATE-CCYYMMDD (8 bytes + 2 bytes of binary
    // storage, low-values) and the 'YYYYMMDD' mask.
    Csutldtc.Result le = csutldtc.call(date + "\u0000\u0000", "YYYYMMDD");
    if (!le.severityCode().equals("0000")) {
      s.year = Flag.NOT_OK;
      s.month = Flag.NOT_OK;
      s.day = Flag.NOT_OK;
      s.fail(
          s.name
              + " validation error Sev code: "
              + le.severityCode()
              + " Message code: "
              + le.messageCode());
    }
    return s.result();
  }

  /**
   * {@code EDIT-DATE-OF-BIRTH}: after a successful {@link #editCcyymmdd}, the date must be strictly
   * before the current business date.
   */
  public Result editDateOfBirth(String fieldName, String ccyymmdd) {
    Result base = editCcyymmdd(fieldName, ccyymmdd);
    if (!base.valid()) {
      return base;
    }
    String today = String.format("%08d", toNumber(clock.today()));
    if (today.compareTo(ccyymmdd.substring(0, 8)) > 0) {
      return base;
    }
    String text = fieldName.strip() + ":cannot be in the future ";
    return new Result(
        Flag.NOT_OK,
        Flag.NOT_OK,
        Flag.NOT_OK,
        true,
        text.length() > RETURN_MSG_LENGTH ? text.substring(0, RETURN_MSG_LENGTH) : text);
  }

  private static int toNumber(java.time.LocalDate date) {
    return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
  }

  private static boolean blank(String part) {
    return part.chars().allMatch(c -> c == ' ') || part.chars().allMatch(c -> c == 0);
  }

  /** Value of an unsigned DISPLAY field, or -1 when {@code IS NUMERIC} would be false. */
  private static int numeric(String part) {
    int value = 0;
    for (int i = 0; i < part.length(); i++) {
      char c = part.charAt(i);
      if (c < '0' || c > '9') {
        return -1;
      }
      value = value * 10 + (c - '0');
    }
    return value;
  }
}
