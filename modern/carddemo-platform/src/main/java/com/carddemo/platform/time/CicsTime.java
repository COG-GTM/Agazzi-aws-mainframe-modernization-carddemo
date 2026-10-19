package com.carddemo.platform.time;

import com.carddemo.contracts.port.BusinessClock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * {@code EXEC CICS ASKTIME} / {@code FORMATTIME} on top of the {@link BusinessClock}. {@code
 * ABSTIME} is the CICS absolute time: milliseconds since 1 January 1900 00:00 local time ({@code
 * PIC S9(15) COMP-3}). The formatting methods return the fixed-width {@code PIC X} values the
 * online programs move into their screens and records.
 */
public final class CicsTime {

  private static final LocalDateTime ABSTIME_EPOCH = LocalDateTime.of(1900, 1, 1, 0, 0);

  private CicsTime() {}

  /** {@code EXEC CICS ASKTIME ABSTIME(...)}. */
  public static long askTime(BusinessClock clock) {
    return toAbsTime(Objects.requireNonNull(clock, "clock").now());
  }

  public static long toAbsTime(LocalDateTime dateTime) {
    return ChronoUnit.MILLIS.between(ABSTIME_EPOCH, dateTime);
  }

  public static LocalDateTime fromAbsTime(long absTime) {
    return ABSTIME_EPOCH.plus(Duration.ofMillis(absTime));
  }

  /** {@code FORMATTIME YYYYMMDD(...) [DATESEP(sep)]}: 8 or 10 bytes. */
  public static String yyyymmdd(long absTime, Character dateSep) {
    LocalDateTime t = fromAbsTime(absTime);
    return join(
        dateSep, "%04d".formatted(t.getYear()), two(t.getMonthValue()), two(t.getDayOfMonth()));
  }

  /** {@code FORMATTIME MMDDYY(...) [DATESEP(sep)]}: 6 or 8 bytes. */
  public static String mmddyy(long absTime, Character dateSep) {
    LocalDateTime t = fromAbsTime(absTime);
    return join(dateSep, two(t.getMonthValue()), two(t.getDayOfMonth()), two(t.getYear() % 100));
  }

  /** {@code FORMATTIME TIME(...) [TIMESEP(sep)]}: 6 or 8 bytes. */
  public static String time(long absTime, Character timeSep) {
    LocalDateTime t = fromAbsTime(absTime);
    return join(timeSep, two(t.getHour()), two(t.getMinute()), two(t.getSecond()));
  }

  /**
   * The 26-byte timestamp built by the online programs after {@code FORMATTIME YYYYMMDD
   * DATESEP('-') TIME TIMESEP(':')} and {@code MOVE ZEROS TO WS-TIMESTAMP-TM-MS6}: {@code
   * YYYY-MM-DD HH:MM:SS.000000} ({@code COBIL00C}, {@code COTRN02C} GET-CURRENT-TIMESTAMP).
   */
  public static String timestamp(long absTime) {
    return yyyymmdd(absTime, '-') + " " + time(absTime, ':') + ".000000";
  }

  /**
   * {@code FUNCTION CURRENT-DATE} fields of {@code CSDAT01Y}: {@code WS-CURDATE} (YYYYMMDD) and
   * {@code WS-CURTIME} (HHMMSSCC, hundredths truncated).
   */
  public static String currentDate(BusinessClock clock) {
    LocalDateTime t = clock.now();
    return "%04d%s%s%s%s%s%s"
        .formatted(
            t.getYear(),
            two(t.getMonthValue()),
            two(t.getDayOfMonth()),
            two(t.getHour()),
            two(t.getMinute()),
            two(t.getSecond()),
            two(t.getNano() / 10_000_000));
  }

  private static String two(int value) {
    return "%02d".formatted(value);
  }

  private static String join(Character sep, String a, String b, String c) {
    return sep == null ? a + b + c : a + sep + b + sep + c;
  }
}
