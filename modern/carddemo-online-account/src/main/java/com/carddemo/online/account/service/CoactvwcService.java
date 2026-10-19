package com.carddemo.online.account.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CoactvwScreen;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.Customer;
import com.carddemo.online.account.support.CobolText;
import com.carddemo.online.account.support.CommArea;
import com.carddemo.online.account.support.ScreenHeader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** CAVW - COACTVWC: account view (paragraphs 0000-MAIN, 1000-SEND-MAP, 2000-PROCESS-INPUTS). */
@Service
public class CoactvwcService {

  public static final String THIS_PGM = "COACTVWC";
  public static final String THIS_TRANID = "CAVW";
  public static final String THIS_MAPSET = "COACTVW";
  public static final String THIS_MAP = "CACTVWA";
  static final String MENU_PGM = "COMEN01C";
  static final String MENU_TRANID = "CM00";

  /** 88 WS-PROMPT-FOR-INPUT. */
  public static final String PROMPT_FOR_INPUT = "Enter or update id of account to display";

  /** 88 WS-PROMPT-FOR-ACCT. */
  public static final String PROMPT_FOR_ACCT = "Account number not provided";

  /** Literal moved by 2210-EDIT-ACCOUNT (double space as in the source). */
  public static final String ACCT_FILTER_INVALID =
      "Account Filter must  be a non-zero 11 digit number";

  /** Program COMMAREA kept between pseudo-conversational turns (CA-CALL-CONTEXT is unused). */
  public record State(ConversationContext contextAtReturn, String lastAcctsid) {}

  private enum Filter {
    BLANK,
    NOT_OK,
    VALID
  }

  private final AccountLookup lookup;
  private final BusinessClock clock;

  public CoactvwcService(AccountLookup lookup, BusinessClock clock) {
    this.lookup = lookup;
    this.clock = clock;
  }

  /**
   * One CAVW task.
   *
   * @param commarea COMMAREA received ({@code null} = {@code EIBCALEN = 0})
   * @param userId signed-on user (kept across {@code INITIALIZE CARDDEMO-COMMAREA})
   * @param userType signed-on user type
   * @param aid AID key
   * @param input received map ({@code null} on first send)
   */
  public ScreenOutcome<CoactvwScreen.Output, State> process(
      ConversationContext commarea,
      String userId,
      UserType userType,
      Action aid,
      CoactvwScreen.Input input) {
    boolean eibcalenZero = commarea == null;
    CommArea ca;
    if (eibcalenZero
        || (MENU_PGM.equals(CobolText.upperTrim(commarea.fromProgram()))
            && commarea.programContext() != ProgramContext.REENTER)) {
      ca = CommArea.initialized(userId, userType);
    } else {
      ca = CommArea.of(commarea);
    }
    Action key = aid == Action.PF3 ? Action.PF3 : Action.ENTER;

    if (key == Action.PF3) {
      return exit(ca);
    }
    Map<String, String> out = new HashMap<>();
    String message = null;
    if (!ca.isReenter()) {
      // 0000-MAIN WHEN CDEMO-PGM-ENTER: 1000-SEND-MAP
      return send(ca, out, null, eibcalenZero);
    }
    // 2000-PROCESS-INPUTS / 2200-EDIT-MAP-INPUTS
    String raw = input == null ? null : input.acctsid();
    String ccAcctId =
        raw == null || "*".equals(raw.strip()) || CobolText.isBlank(raw)
            ? null
            : CobolText.pad(raw, 11);
    Filter filter;
    if (ccAcctId == null) {
      // 2210: blank sets the prompt, then fails the NUMERIC test (LOW-VALUES) -> NOT-OK
      message = PROMPT_FOR_ACCT;
      ca.acctId = 0L;
      filter = Filter.NOT_OK;
    } else if (!CobolText.isNumeric(ccAcctId) || Long.parseLong(ccAcctId) == 0) {
      message = ACCT_FILTER_INVALID;
      ca.acctId = 0L;
      filter = Filter.NOT_OK;
    } else {
      ca.acctId = Long.parseLong(ccAcctId);
      filter = Filter.VALID;
    }
    out.put("ACCTSID", ccAcctId);
    if (filter == Filter.VALID) {
      // 9000-READ-ACCT
      AccountLookup.Result r = lookup.read(ca.acctId);
      if (r.xref() != null) {
        ca.custId = r.xref().getXrefCustId().longValue();
        ca.cardNum = parseCard(r.xref().getXrefCardNum());
      }
      message = r.message();
      if (r.foundAccount() || r.foundCustomer()) {
        putAccount(out, r.account());
      }
      if (r.foundCustomer()) {
        putCustomer(out, r.customer());
      }
    }
    return send(ca, out, message, eibcalenZero);
  }

  private ScreenOutcome<CoactvwScreen.Output, State> exit(CommArea ca) {
    ca.toTranid = CommArea.blank(ca.fromTranid) ? MENU_TRANID : ca.fromTranid;
    ca.toProgram = CommArea.blank(ca.fromProgram) ? MENU_PGM : ca.fromProgram;
    ca.fromTranid = THIS_TRANID;
    ca.fromProgram = THIS_PGM;
    ca.userType = UserType.USER;
    ca.pgmContext = ProgramContext.ENTER;
    ca.lastMapset = THIS_MAPSET;
    ca.lastMap = THIS_MAP;
    ConversationContext ctx = ca.toContext();
    ScreenResponse<CoactvwScreen.Output> resp =
        ScreenResponse.<CoactvwScreen.Output>of(ScreenId.COACTVW, null, ctx)
            .withNextScreen(screenOf(ca.toProgram));
    return new ScreenOutcome<>(resp, ctx, null);
  }

  static ScreenId screenOf(String program) {
    String p = CobolText.upperTrim(program);
    for (ScreenId s : ScreenId.values()) {
      if (s.program().equals(p)) {
        return s;
      }
    }
    return ScreenId.COMEN01;
  }

  private ScreenOutcome<CoactvwScreen.Output, State> send(
      CommArea ca, Map<String, String> out, String message, boolean eibcalenZero) {
    String errmsg = message == null ? null : CobolText.pad(message, 75).stripTrailing();
    ca.pgmContext = ProgramContext.REENTER;
    ConversationContext ctx = ca.toContext();
    ScreenHeader h = ScreenHeader.of(clock);
    out.put("INFOMSG", PROMPT_FOR_INPUT);
    out.put("ERRMSG", errmsg);
    CoactvwScreen.Output o = output(h, out);
    List<ScreenMessage> messages = new ArrayList<>();
    List<FieldError> errors = new ArrayList<>();
    if (errmsg != null) {
      messages.add(ScreenMessage.error(errmsg));
      errors.add(new FieldError("ACCTSID", errmsg));
    }
    messages.add(ScreenMessage.info(PROMPT_FOR_INPUT));
    ScreenResponse<CoactvwScreen.Output> resp =
        new ScreenResponse<>(ScreenId.COACTVW, o, messages, null, errors, ctx);
    return new ScreenOutcome<>(resp, ctx, new State(ctx, o.acctsid()));
  }

  private static Long parseCard(String card) {
    String c = card == null ? "" : card.strip();
    return CobolText.isNumeric(c) ? Long.valueOf(c) : null;
  }

  private static void putAccount(Map<String, String> out, Account a) {
    if (a == null) {
      return;
    }
    out.put("ACSTTUS", a.getAcctActiveStatus());
    out.put("ACURBAL", CobolText.formatAmount(a.getAcctCurrBal()));
    out.put("ACRDLIM", CobolText.formatAmount(a.getAcctCreditLimit()));
    out.put("ACSHLIM", CobolText.formatAmount(a.getAcctCashCreditLimit()));
    out.put("ACRCYCR", CobolText.formatAmount(a.getAcctCurrCycCredit()));
    out.put("ACRCYDB", CobolText.formatAmount(a.getAcctCurrCycDebit()));
    out.put("ADTOPEN", a.getAcctOpenDate());
    out.put("AEXPDT", a.getAcctExpirationDate());
    out.put("AREISDT", a.getAcctReissueDate());
    out.put("AADDGRP", a.getAcctGroupId());
  }

  private static void putCustomer(Map<String, String> out, Customer c) {
    String ssn = CobolText.zoned(c.getCustSsn(), 9);
    out.put("ACSTNUM", CobolText.zoned(c.getCustId(), 9));
    out.put("ACSTSSN", ssn.substring(0, 3) + "-" + ssn.substring(3, 5) + "-" + ssn.substring(5));
    out.put("ACSTFCO", CobolText.zoned(c.getCustFicoCreditScore(), 3));
    out.put("ACSTDOB", c.getCustDobYyyyMmDd());
    out.put("ACSFNAM", c.getCustFirstName());
    out.put("ACSMNAM", c.getCustMiddleName());
    out.put("ACSLNAM", c.getCustLastName());
    out.put("ACSADL1", c.getCustAddrLine1());
    out.put("ACSADL2", c.getCustAddrLine2());
    out.put("ACSCITY", c.getCustAddrLine3());
    out.put("ACSSTTE", c.getCustAddrStateCd());
    out.put("ACSZIPC", c.getCustAddrZip());
    out.put("ACSCTRY", c.getCustAddrCountryCd());
    out.put("ACSPHN1", c.getCustPhoneNum1());
    out.put("ACSPHN2", c.getCustPhoneNum2());
    out.put("ACSGOVT", c.getCustGovtIssuedId());
    out.put("ACSEFTC", c.getCustEftAccountId());
    out.put("ACSPFLG", c.getCustPriCardHolderInd());
  }

  private static CoactvwScreen.Output output(ScreenHeader h, Map<String, String> m) {
    return new CoactvwScreen.Output(
        THIS_TRANID,
        h.title01(),
        h.curdate(),
        THIS_PGM,
        h.title02(),
        h.curtime(),
        f(m, "ACCTSID", 11),
        f(m, "ACSTTUS", 1),
        f(m, "ADTOPEN", 10),
        f(m, "ACRDLIM", 15),
        f(m, "AEXPDT", 10),
        f(m, "ACSHLIM", 15),
        f(m, "AREISDT", 10),
        f(m, "ACURBAL", 15),
        f(m, "ACRCYCR", 15),
        f(m, "AADDGRP", 10),
        f(m, "ACRCYDB", 15),
        f(m, "ACSTNUM", 9),
        f(m, "ACSTSSN", 12),
        f(m, "ACSTDOB", 10),
        f(m, "ACSTFCO", 3),
        f(m, "ACSFNAM", 25),
        f(m, "ACSMNAM", 25),
        f(m, "ACSLNAM", 25),
        f(m, "ACSADL1", 50),
        f(m, "ACSSTTE", 2),
        f(m, "ACSADL2", 50),
        f(m, "ACSZIPC", 5),
        f(m, "ACSCITY", 50),
        f(m, "ACSCTRY", 3),
        f(m, "ACSPHN1", 13),
        f(m, "ACSGOVT", 20),
        f(m, "ACSPHN2", 13),
        f(m, "ACSEFTC", 10),
        f(m, "ACSPFLG", 1),
        f(m, "INFOMSG", 45),
        f(m, "ERRMSG", 78));
  }

  /** MOVE to the map output field {@code PIC X(len)}, presentation-trimmed. */
  static String f(Map<String, String> m, String field, int len) {
    String v = m.get(field);
    return v == null ? null : CobolText.display(CobolText.pad(v, len));
  }
}
