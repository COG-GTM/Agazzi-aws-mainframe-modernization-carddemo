package com.carddemo.online.card.mapper;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.ScreenId;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

/** Shared helpers of the O3 programs: COTTL01Y/CSDAT01Y header, PIC X edits and padding. */
public final class CardScreenSupport {

  public static final String TITLE01 = "      AWS Mainframe Modernization       ";
  public static final String TITLE02 = "              CardDemo                  ";
  public static final String MENU_TRANID = "CM00";
  public static final String MENU_PGM = "COMEN01C";

  private static final DateTimeFormatter CURDATE = DateTimeFormatter.ofPattern("MM/dd/yy");
  private static final DateTimeFormatter CURTIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private CardScreenSupport() {}

  /** Map header of 1100-SCREEN-INIT. */
  public record Header(String curdate, String curtime) {
    public static Header of(BusinessClock clock) {
      LocalDateTime now = clock.now();
      return new Header(CURDATE.format(now), CURTIME.format(now));
    }
  }

  /** Blank in the CICS sense: null, empty, spaces or low-values. */
  public static boolean isBlank(String s) {
    return s == null || s.chars().allMatch(c -> c == ' ' || c == 0);
  }

  /** Map input where {@code '*'} or spaces mean "not supplied" (1100/2100-RECEIVE-MAP). */
  public static String receive(String s) {
    if (s == null || isBlank(s) || "*".equals(s.strip())) {
      return null;
    }
    return s;
  }

  /** {@code PIC X(n) IS NUMERIC}: exactly {@code n} digits (trailing spaces are not numeric). */
  public static boolean isNumeric(String s, int length) {
    return s != null && s.length() == length && s.chars().allMatch(Character::isDigit);
  }

  /** {@code PIC X(n) = ZEROS} on its numeric redefinition. */
  public static boolean isZeros(String s) {
    return s != null && !s.isEmpty() && s.chars().allMatch(c -> c == '0');
  }

  public static String padRight(String s, int length) {
    String v = s == null ? "" : s;
    if (v.length() >= length) {
      return v.substring(0, length);
    }
    char[] pad = new char[length - v.length()];
    Arrays.fill(pad, ' ');
    return v + new String(pad);
  }

  public static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.stripTrailing();
    return t.isEmpty() ? null : t;
  }

  public static String accountText(Long accountId) {
    return accountId == null || accountId == 0 ? null : "%011d".formatted(accountId);
  }

  public static String cardText(Long cardNumber) {
    return cardNumber == null || cardNumber == 0 ? null : "%016d".formatted(cardNumber);
  }

  /** Screen of an XCTL target program, for {@code ScreenResponse.nextScreen}. */
  public static Optional<ScreenId> screenOfProgram(String program) {
    return Arrays.stream(ScreenId.values()).filter(s -> s.program().equals(program)).findFirst();
  }
}
