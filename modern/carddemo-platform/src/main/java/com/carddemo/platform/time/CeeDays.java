package com.carddemo.platform.time;

import com.carddemo.contracts.port.BusinessClock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Java replacement for the Language Environment callable service {@code CEEDAYS} as used by {@code
 * CSUTLDTC}: converts a character date described by a picture string into a Lillian day number
 * (days since 14 October 1582, so 15 October 1582 is day 1) and reports failures through the same
 * condition tokens ({@code CEE2507}…{@code CEE2521}).
 *
 * <p>Supported picture tokens are {@code YYYY}, {@code YY}, {@code MM}, {@code DD}, {@code DDD}
 * (Julian day); any non-letter is a literal separator that must match the date. Two-digit years are
 * windowed like {@code CEESCEN} with the default 80-year look-back from the {@link BusinessClock}.
 * The checks run in a fixed order (picture, length, digits/separators, month, day, range) mirrored
 * by the {@code CEEDAYS} stub of the golden harness.
 */
public final class CeeDays {

  /** First valid Lillian day: 15 October 1582. */
  public static final LocalDate LILLIAN_DAY_ONE = LocalDate.of(1582, 10, 15);

  private static final LocalDate LILLIAN_EPOCH = LILLIAN_DAY_ONE.minusDays(1);
  private static final int CENTURY_WINDOW_LOOKBACK = 80;

  /** {@code CEEDAYS} condition (the {@code Msg-No} of the 12-byte feedback token). */
  public enum Feedback {
    VALID(0),
    INSUFFICIENT_DATA(2507),
    BAD_DATE_VALUE(2508),
    INVALID_ERA(2509),
    UNSUPPORTED_RANGE(2513),
    INVALID_MONTH(2517),
    BAD_PIC_STRING(2518),
    NON_NUMERIC_DATA(2520),
    YEAR_IN_ERA_ZERO(2521);

    private final int messageNumber;

    Feedback(int messageNumber) {
      this.messageNumber = messageNumber;
    }

    public int messageNumber() {
      return messageNumber;
    }

    /** Condition severity: 0 when valid, 3 (error) otherwise. */
    public int severity() {
      return this == VALID ? 0 : 3;
    }

    /** First 8 bytes of the feedback code, e.g. {@code 000309CB59C3C5C5} for CEE2507. */
    public String tokenHex() {
      if (this == VALID) {
        return "0000000000000000";
      }
      return String.format(Locale.ROOT, "%04X%04X59C3C5C5", severity(), messageNumber);
    }
  }

  /** Result of a conversion: Lillian day number (0 on error) and feedback condition. */
  public record Outcome(int lillian, Feedback feedback) {
    public boolean valid() {
      return feedback == Feedback.VALID;
    }
  }

  private enum Kind {
    YEAR4,
    YEAR2,
    MONTH,
    DAY,
    JULIAN,
    LITERAL
  }

  private record Token(Kind kind, int offset, int length) {}

  private final BusinessClock clock;

  public CeeDays(BusinessClock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Converts {@code date} according to {@code picture}. Low-values count as spaces and trailing
   * spaces are ignored on both arguments, matching the fixed-length {@code VSTRING} buffers passed
   * by {@code CSUTLDTC}.
   */
  public Outcome toLillian(String date, String picture) {
    String pic = trimTrailing(picture);
    String value = trimTrailing(date);
    List<Token> tokens = parse(pic);
    if (tokens == null) {
      return error(Feedback.BAD_PIC_STRING);
    }
    if (value.length() < pic.length()) {
      return error(Feedback.INSUFFICIENT_DATA);
    }
    int year = -1;
    int month = -1;
    int day = -1;
    int julian = -1;
    boolean twoDigitYear = false;
    for (Token token : tokens) {
      String part = value.substring(token.offset(), token.offset() + token.length());
      if (token.kind() == Kind.LITERAL) {
        if (!part.equals(pic.substring(token.offset(), token.offset() + token.length()))) {
          return error(Feedback.NON_NUMERIC_DATA);
        }
        continue;
      }
      if (!isDigits(part)) {
        return error(Feedback.NON_NUMERIC_DATA);
      }
      int number = Integer.parseInt(part);
      switch (token.kind()) {
        case YEAR4 -> year = number;
        case YEAR2 -> {
          year = number;
          twoDigitYear = true;
        }
        case MONTH -> month = number;
        case DAY -> day = number;
        case JULIAN -> julian = number;
        default -> throw new IllegalStateException(token.kind().name());
      }
    }
    if (twoDigitYear) {
      year = windowYear(year);
    }
    if (julian < 0 && (month < 1 || month > 12)) {
      return error(Feedback.INVALID_MONTH);
    }
    if (julian >= 0) {
      if (julian < 1 || julian > (isLeap(year) ? 366 : 365)) {
        return error(Feedback.BAD_DATE_VALUE);
      }
    } else if (day < 1 || day > daysInMonth(year, month)) {
      return error(Feedback.BAD_DATE_VALUE);
    }
    if (year < LILLIAN_DAY_ONE.getYear()) {
      return error(Feedback.UNSUPPORTED_RANGE);
    }
    LocalDate resolved =
        julian >= 0 ? LocalDate.ofYearDay(year, julian) : LocalDate.of(year, month, day);
    if (resolved.isBefore(LILLIAN_DAY_ONE)) {
      return error(Feedback.UNSUPPORTED_RANGE);
    }
    return new Outcome((int) ChronoUnit.DAYS.between(LILLIAN_EPOCH, resolved), Feedback.VALID);
  }

  /** Inverse of {@link #toLillian}: the calendar date of a Lillian day number. */
  public static LocalDate fromLillian(int lillian) {
    if (lillian < 1) {
      throw new IllegalArgumentException("Lillian day must be >= 1: " + lillian);
    }
    return LILLIAN_EPOCH.plusDays(lillian);
  }

  /** Gregorian leap-year rule, as applied by CEEDAYS. */
  public static boolean isLeap(int year) {
    return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
  }

  static int daysInMonth(int year, int month) {
    return switch (month) {
      case 2 -> isLeap(year) ? 29 : 28;
      case 4, 6, 9, 11 -> 30;
      default -> 31;
    };
  }

  private int windowYear(int yy) {
    int start = clock.today().getYear() - CENTURY_WINDOW_LOOKBACK;
    int candidate = (start / 100) * 100 + yy;
    return candidate < start ? candidate + 100 : candidate;
  }

  private static List<Token> parse(String pic) {
    if (pic.isEmpty()) {
      return null;
    }
    List<Token> tokens = new ArrayList<>();
    boolean year = false;
    boolean month = false;
    boolean day = false;
    boolean julian = false;
    int i = 0;
    while (i < pic.length()) {
      String rest = pic.substring(i);
      Token token;
      if (rest.startsWith("YYYY")) {
        token = new Token(Kind.YEAR4, i, 4);
      } else if (rest.startsWith("YY")) {
        token = new Token(Kind.YEAR2, i, 2);
      } else if (rest.startsWith("MM")) {
        token = new Token(Kind.MONTH, i, 2);
      } else if (rest.startsWith("DDD")) {
        token = new Token(Kind.JULIAN, i, 3);
      } else if (rest.startsWith("DD")) {
        token = new Token(Kind.DAY, i, 2);
      } else if (Character.isLetter(rest.charAt(0))) {
        return null;
      } else {
        token = new Token(Kind.LITERAL, i, 1);
      }
      switch (token.kind()) {
        case YEAR4, YEAR2 -> {
          if (year) {
            return null;
          }
          year = true;
        }
        case MONTH -> {
          if (month) {
            return null;
          }
          month = true;
        }
        case DAY -> {
          if (day) {
            return null;
          }
          day = true;
        }
        case JULIAN -> {
          if (julian) {
            return null;
          }
          julian = true;
        }
        default -> {}
      }
      tokens.add(token);
      i += token.length();
    }
    boolean complete = year && (julian ? !month && !day : month && day);
    return complete ? tokens : null;
  }

  private static boolean isDigits(String part) {
    for (int i = 0; i < part.length(); i++) {
      char c = part.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  static String trimTrailing(String value) {
    if (value == null) {
      return "";
    }
    value = value.replace('\0', ' ');
    int end = value.length();
    while (end > 0 && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\0')) {
      end--;
    }
    return value.substring(0, end);
  }

  private static Outcome error(Feedback feedback) {
    return new Outcome(0, feedback);
  }
}
