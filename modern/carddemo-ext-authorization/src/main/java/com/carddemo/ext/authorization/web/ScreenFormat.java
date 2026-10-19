package com.carddemo.ext.authorization.web;

import com.carddemo.contracts.port.BusinessClock;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/** BMS edit helpers shared by CPVS/CPVD. */
final class ScreenFormat {
  static final String TITLE01 = "      AWS Mainframe Modernization       ";
  static final String TITLE02 = "              CardDemo                  ";
  static final String INVALID_KEY = "Invalid key pressed. Please see below...";

  private ScreenFormat() {}

  /** {@code -zzzzzzz9.99} (intDigits=8) or {@code -zzzz9.99} (intDigits=5). */
  static String amount(BigDecimal v, int intDigits) {
    BigDecimal a = (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.DOWN);
    BigDecimal abs = a.abs();
    long ip = abs.toBigInteger().mod(BigInteger.TEN.pow(intDigits)).longValue();
    int cents = abs.remainder(BigDecimal.ONE).movePointRight(2).intValue();
    return (a.signum() < 0 ? "-" : " ")
        + String.format("%" + intDigits + "d", ip)
        + '.'
        + String.format("%02d", cents);
  }

  /** YYMMDD -> MM/DD/YY. */
  static String date(String yymmdd) {
    String s = AuthTexts.pad(yymmdd, 6);
    return s.substring(2, 4) + '/' + s.substring(4, 6) + '/' + s.substring(0, 2);
  }

  /** HHMMSS -> HH:MM:SS. */
  static String time(String hhmmss) {
    String s = AuthTexts.pad(hhmmss, 6);
    return s.substring(0, 2) + ':' + s.substring(2, 4) + ':' + s.substring(4, 6);
  }

  static String curdate(BusinessClock c) {
    return DateTimeFormatter.ofPattern("MM/dd/yy").format(c.now());
  }

  static String curtime(BusinessClock c) {
    return DateTimeFormatter.ofPattern("HH:mm:ss").format(c.now());
  }

  static final class AuthTexts {
    private AuthTexts() {}

    static String pad(String v, int len) {
      return com.carddemo.ext.authorization.codec.AuthorizationRequest.pad(v, len);
    }
  }
}
