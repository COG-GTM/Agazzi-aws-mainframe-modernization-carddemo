package com.carddemo.platform.time;

import java.util.Objects;

/**
 * Port of the {@code COBDATFT} assembler routine (parameter block {@code CODATECN}). Converts
 * between the unseparated {@code YYYYMMDD} (type {@code '1'}) and separated {@code YYYY-MM-DD}
 * (type {@code '2'}) layouts by byte copy, exactly as the assembler does: no calendar check is
 * performed and the input is never parsed as a date.
 */
public final class CobolDateFormatter {

  public static final char TYPE_YYYYMMDD = '1';
  public static final char TYPE_YYYY_MM_DD = '2';
  public static final String INVALID_INPUT = "INVALID INPUT";

  /** Length of {@code CODATECN-INP-DATE} / {@code CODATECN-0UT-DATE}. */
  public static final int DATE_LENGTH = 20;

  /** Length of {@code CODATECN-ERROR-MSG}. */
  public static final int ERROR_LENGTH = 38;

  private CobolDateFormatter() {}

  /**
   * Outcome of a {@code COBDATFT} call: the 20-byte output date and the 38-byte error message. Both
   * fields keep the {@code PIC X} padding of the copybook; on error the output date is left
   * untouched (blank in the callers, which initialise the block).
   */
  public record Result(String outputDate, String errorMessage) {
    public boolean valid() {
      return errorMessage.isBlank();
    }

    /** Output date without the trailing {@code PIC X} padding. */
    public String date() {
      return outputDate.stripTrailing();
    }
  }

  /**
   * Runs the conversion.
   *
   * @param inputType {@code CODATECN-TYPE}: {@code '1'} or {@code '2'}
   * @param inputDate {@code CODATECN-INP-DATE}; padded or truncated to 20 bytes
   * @param outputType {@code CODATECN-OUTTYPE}: {@code '1'} or {@code '2'}
   */
  public static Result convert(char inputType, String inputDate, char outputType) {
    String in = pad(Objects.requireNonNull(inputDate, "inputDate"), DATE_LENGTH);
    char[] out = blank(DATE_LENGTH);
    switch (inputType) {
      case TYPE_YYYYMMDD -> {
        if (in.charAt(4) == '-' || outputType == TYPE_YYYY_MM_DD) {
          return error();
        }
        in.getChars(0, 4, out, 0);
        out[4] = '-';
        in.getChars(4, 6, out, 5);
        out[7] = '-';
        in.getChars(6, 8, out, 8);
      }
      case TYPE_YYYY_MM_DD -> {
        if (outputType == TYPE_YYYYMMDD) {
          return error();
        }
        in.getChars(0, 4, out, 0);
        in.getChars(5, 7, out, 4);
        in.getChars(8, 10, out, 6);
      }
      default -> {
        return error();
      }
    }
    return new Result(new String(out), pad("", ERROR_LENGTH));
  }

  /**
   * {@code YYYYMMDD} to {@code YYYY-MM-DD}. The assembler only accepts an output type equal to the
   * input type (type {@code '1'} rejects output {@code '2'} and vice versa).
   */
  public static Result toSeparated(String yyyymmdd) {
    return convert(TYPE_YYYYMMDD, yyyymmdd, TYPE_YYYYMMDD);
  }

  /** {@code YYYY-MM-DD} to {@code YYYYMMDD}, as called by {@code CBACT01C} (type 2 / 2). */
  public static Result toUnseparated(String yyyyMmDd) {
    return convert(TYPE_YYYY_MM_DD, yyyyMmDd, TYPE_YYYY_MM_DD);
  }

  private static Result error() {
    return new Result(pad("", DATE_LENGTH), pad(INVALID_INPUT, ERROR_LENGTH));
  }

  private static char[] blank(int length) {
    char[] chars = new char[length];
    java.util.Arrays.fill(chars, ' ');
    return chars;
  }

  static String pad(String value, int length) {
    if (value.length() >= length) {
      return value.substring(0, length);
    }
    return value + " ".repeat(length - value.length());
  }
}
