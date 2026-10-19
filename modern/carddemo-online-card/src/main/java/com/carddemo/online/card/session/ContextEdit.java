package com.carddemo.online.card.session;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;

/**
 * Mutable copy of the immutable {@link ConversationContext} (the COBOL MOVEs to CARDDEMO-COMMAREA).
 */
public final class ContextEdit {

  private String fromTran;
  private String fromPgm;
  private String toTran;
  private String toPgm;
  private final String userId;
  private final ConversationContext.UserType userType;
  private ProgramContext pgmContext;
  private final Long customerId;
  private final String fname;
  private final String mname;
  private final String lname;
  private Long accountId;
  private final String accountStatus;
  private Long cardNumber;
  private String lastMap;
  private String lastMapset;

  private ContextEdit(ConversationContext c) {
    fromTran = c.fromTransactionId();
    fromPgm = c.fromProgram();
    toTran = c.toTransactionId();
    toPgm = c.toProgram();
    userId = c.userId();
    userType = c.userType();
    pgmContext = c.programContext();
    customerId = c.customerId();
    fname = c.customerFirstName();
    mname = c.customerMiddleName();
    lname = c.customerLastName();
    accountId = c.accountId();
    accountStatus = c.accountStatus();
    cardNumber = c.cardNumber();
    lastMap = c.lastMap();
    lastMapset = c.lastMapset();
  }

  public static ContextEdit of(ConversationContext c) {
    return new ContextEdit(c);
  }

  public ContextEdit from(String tran, String pgm) {
    fromTran = tran;
    fromPgm = pgm;
    return this;
  }

  public ContextEdit to(String tran, String pgm) {
    toTran = tran;
    toPgm = pgm;
    return this;
  }

  public ContextEdit enter() {
    pgmContext = ProgramContext.ENTER;
    return this;
  }

  public ContextEdit reenter() {
    pgmContext = ProgramContext.REENTER;
    return this;
  }

  public ContextEdit account(Long id) {
    accountId = id;
    return this;
  }

  public ContextEdit card(Long num) {
    cardNumber = num;
    return this;
  }

  public ContextEdit lastMap(String map, String mapset) {
    lastMap = map;
    lastMapset = mapset;
    return this;
  }

  public ConversationContext build() {
    return new ConversationContext(
        fromTran,
        fromPgm,
        toTran,
        toPgm,
        userId,
        userType,
        pgmContext,
        customerId,
        fname,
        mname,
        lname,
        accountId,
        accountStatus,
        cardNumber,
        lastMap,
        lastMapset);
  }
}
