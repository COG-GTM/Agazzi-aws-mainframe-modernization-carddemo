package com.carddemo.ext.trantype.online;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Header fields (COTTL01Y + CSDAT01Y) and DB2 message formatting shared by CTLI/CTTU. */
final class ScreenSupport {

  static final String TITLE01 = "      AWS Mainframe Modernization       ";
  static final String TITLE02 = "              CardDemo                  ";
  static final String ADMIN_TRANID = "CA00";
  static final String ADMIN_PGM = "COADM01C";
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yy");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private ScreenSupport() {}

  static String date(BusinessClock clock) {
    return DATE.format(now(clock));
  }

  static String time(BusinessClock clock) {
    return TIME.format(now(clock));
  }

  private static LocalDateTime now(BusinessClock clock) {
    return clock.now();
  }

  /** PIC ----9 edit of SQLCODE (WS-DISP-SQLCODE / WS-VAR-SQLCODE). */
  static String sqlcode(int code) {
    return String.format("%5d", code);
  }

  /** 9999-FORMAT-DB2-MESSAGE: action + ' SQLCODE:' + code + ' ' + DSNTIAC text. */
  static String db2Message(String action, int code, String text) {
    return action.strip() + " SQLCODE:" + sqlcode(code) + " " + (text == null ? "" : text);
  }

  static ConversationContext context(String userId, String tranId, String program) {
    return ConversationContext.signedOn(userId, UserType.ADMIN)
        .transferTo(tranId, program, tranId, program)
        .reenter();
  }

  static ConversationContext transfer(
      String userId, String fromTran, String fromPgm, String toTran, String toPgm) {
    return ConversationContext.signedOn(userId, UserType.ADMIN)
        .transferTo(fromTran, fromPgm, toTran, toPgm);
  }

  static boolean isNumeric2(String s) {
    return s != null
        && s.length() == 2
        && Character.isDigit(s.charAt(0))
        && Character.isDigit(s.charAt(1));
  }

  /** 1240-EDIT-ALPHANUM-REQD: letters, digits and spaces only. */
  static boolean isAlphanumeric(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok =
          (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ';
      if (!ok) {
        return false;
      }
    }
    return true;
  }
}
