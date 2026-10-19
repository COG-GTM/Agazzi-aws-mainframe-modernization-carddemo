package com.carddemo.contracts.screen;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

/**
 * Pseudo-conversational state, mirror of COCOM01Y {@code CARDDEMO-COMMAREA}. Stored in the HTTP
 * session (Spring Session JDBC, O1) instead of {@code RETURN TRANSID COMMAREA}.
 */
public record ConversationContext(
    String fromTransactionId,
    String fromProgram,
    String toTransactionId,
    String toProgram,
    String userId,
    UserType userType,
    ProgramContext programContext,
    Long customerId,
    String customerFirstName,
    String customerMiddleName,
    String customerLastName,
    Long accountId,
    String accountStatus,
    Long cardNumber,
    String lastMap,
    String lastMapset)
    implements Serializable {

  /**
   * HTTP session attribute holding the current context, shared by every online module
   * (contracts-v1.1). The value is the {@link ConversationContext} itself, not its JSON.
   */
  public static final String SESSION_ATTRIBUTE =
      "com.carddemo.contracts.screen.ConversationContext";

  /** CDEMO-USER-TYPE: 88 CDEMO-USRTYP-ADMIN 'A', CDEMO-USRTYP-USER 'U'. */
  public enum UserType {
    ADMIN('A'),
    USER('U');

    private final char code;

    UserType(char code) {
      this.code = code;
    }

    public char code() {
      return code;
    }

    public static UserType fromCode(char code) {
      for (UserType t : values()) {
        if (t.code == code) {
          return t;
        }
      }
      throw new IllegalArgumentException("Unknown SEC-USR-TYPE: " + code);
    }
  }

  /** CDEMO-PGM-CONTEXT: 88 CDEMO-PGM-ENTER 0, CDEMO-PGM-REENTER 1. */
  public enum ProgramContext {
    ENTER,
    REENTER
  }

  /** Context right after a successful sign-on (COSGN00C). */
  public static ConversationContext signedOn(String userId, UserType userType) {
    return new ConversationContext(
        "CC00",
        "COSGN00C",
        null,
        null,
        userId,
        userType,
        ProgramContext.ENTER,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /** XCTL to another program: from = current, to = target, context reset to ENTER. */
  public ConversationContext transferTo(
      String fromTran, String fromPgm, String toTran, String toPgm) {
    return new ConversationContext(
        fromTran,
        fromPgm,
        toTran,
        toPgm,
        userId,
        userType,
        ProgramContext.ENTER,
        customerId,
        customerFirstName,
        customerMiddleName,
        customerLastName,
        accountId,
        accountStatus,
        cardNumber,
        lastMap,
        lastMapset);
  }

  public ConversationContext reenter() {
    return new ConversationContext(
        fromTransactionId,
        fromProgram,
        toTransactionId,
        toProgram,
        userId,
        userType,
        ProgramContext.REENTER,
        customerId,
        customerFirstName,
        customerMiddleName,
        customerLastName,
        accountId,
        accountStatus,
        cardNumber,
        lastMap,
        lastMapset);
  }

  @JsonIgnore
  public boolean isAdmin() {
    return userType == UserType.ADMIN;
  }
}
