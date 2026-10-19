package com.carddemo.online.account.support;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;

/** Mutable working copy of {@code CARDDEMO-COMMAREA} (COCOM01Y) while a paragraph runs. */
public final class CommArea {

  public String fromTranid;
  public String fromProgram;
  public String toTranid;
  public String toProgram;
  public String userId;
  public UserType userType;
  public ProgramContext pgmContext;
  public Long custId;
  public String custFname;
  public String custMname;
  public String custLname;
  public Long acctId;
  public String acctStatus;
  public Long cardNum;
  public String lastMap;
  public String lastMapset;

  public static CommArea of(ConversationContext c) {
    CommArea a = new CommArea();
    a.fromTranid = c.fromTransactionId();
    a.fromProgram = c.fromProgram();
    a.toTranid = c.toTransactionId();
    a.toProgram = c.toProgram();
    a.userId = c.userId();
    a.userType = c.userType();
    a.pgmContext = c.programContext() == null ? ProgramContext.ENTER : c.programContext();
    a.custId = c.customerId();
    a.custFname = c.customerFirstName();
    a.custMname = c.customerMiddleName();
    a.custLname = c.customerLastName();
    a.acctId = c.accountId();
    a.acctStatus = c.accountStatus();
    a.cardNum = c.cardNumber();
    a.lastMap = c.lastMap();
    a.lastMapset = c.lastMapset();
    return a;
  }

  /**
   * {@code INITIALIZE CARDDEMO-COMMAREA}. The signed-on identity is kept: in Java it comes from
   * Spring Security, not from the COMMAREA bytes.
   */
  public static CommArea initialized(String userId, UserType userType) {
    CommArea a = new CommArea();
    a.userId = userId;
    a.userType = userType;
    a.pgmContext = ProgramContext.ENTER;
    return a;
  }

  public boolean isReenter() {
    return pgmContext == ProgramContext.REENTER;
  }

  public ConversationContext toContext() {
    return new ConversationContext(
        fromTranid,
        fromProgram,
        toTranid,
        toProgram,
        userId,
        userType,
        pgmContext,
        custId,
        custFname,
        custMname,
        custLname,
        acctId,
        acctStatus,
        cardNum,
        lastMap,
        lastMapset);
  }

  /** {@code CDEMO-FROM-TRANID/PROGRAM EQUAL LOW-VALUES OR SPACES}. */
  public static boolean blank(String s) {
    return CobolText.isBlank(s);
  }
}
