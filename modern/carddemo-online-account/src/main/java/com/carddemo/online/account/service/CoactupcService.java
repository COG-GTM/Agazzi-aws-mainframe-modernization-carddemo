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
import com.carddemo.contracts.screen.map.CoactupScreen;
import com.carddemo.online.account.support.CobolText;
import com.carddemo.online.account.support.CommArea;
import com.carddemo.online.account.support.ScreenFields;
import com.carddemo.online.account.support.ScreenHeader;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/** CAUP - COACTUPC: account + customer update (0000-MAIN, 1000..3000, 9000, 9600). */
@Service
public class CoactupcService {

  public static final String THIS_PGM = "COACTUPC";
  public static final String THIS_TRANID = "CAUP";
  public static final String THIS_MAPSET = "COACTUP";
  public static final String THIS_MAP = "CACTUPA";

  static final String PROMPT_FOR_SEARCH_KEYS = "Enter or update id of account to update";
  static final String PROMPT_FOR_CHANGES = "Update account details presented above.";
  static final String PROMPT_FOR_CONFIRMATION = "Changes validated.Press F5 to save";
  static final String CONFIRM_UPDATE_SUCCESS = "Changes committed to database";
  static final String INFORM_FAILURE = "Changes unsuccessful. Please try again";
  static final String NO_SEARCH_CRITERIA = "No input received";
  static final String NO_CHANGES_DETECTED = "No change detected with respect to values fetched.";
  static final String ACCT_INVALID =
      "Account Number if supplied must be a 11 digit Non-Zero Number";
  static final String COULD_NOT_LOCK_ACCT = "Could not lock account record for update";
  static final String COULD_NOT_LOCK_CUST = "Could not lock customer record for update";
  static final String DATA_CHANGED = "Record changed by some one else. Please review";
  static final String UPDATE_FAILED = "Update of record failed";

  /** ACUP-CHANGE-ACTION. */
  public enum ChangeAction {
    NOT_FETCHED,
    SHOW_DETAILS,
    CHANGES_NOT_OK,
    CHANGES_OK_NOT_CONFIRMED,
    CHANGES_OKAYED_AND_DONE,
    CHANGES_OKAYED_LOCK_ERROR,
    CHANGES_OKAYED_BUT_FAILED;

    boolean changesMade() {
      return ordinal() >= CHANGES_NOT_OK.ordinal();
    }
  }

  /** WS-THIS-PROGCOMMAREA plus the screen last sent (3270 FSET echo) and the COMMAREA. */
  public record State(
      ChangeAction action,
      AccountSnapshot old,
      Map<String, String> newFields,
      Map<String, BigDecimal> newAmounts,
      ConversationContext contextAtReturn,
      Map<String, String> lastScreen)
      implements Serializable {}

  /** Input fields: map name, BMS length, JUSTIFY=(RIGHT). */
  private static final Map<String, Integer> LENGTHS = new LinkedHashMap<>();

  private static final Set<String> RIGHT_JUSTIFIED =
      Set.of(
          "OPNYEAR", "OPNMON", "OPNDAY", "EXPYEAR", "EXPMON", "EXPDAY", "RISYEAR", "RISMON",
          "RISDAY", "DOBYEAR", "DOBMON", "DOBDAY", "ACSPH1A", "ACSPH1B", "ACSPH1C", "ACSPH2A",
          "ACSPH2B", "ACSPH2C");

  private static final List<String> AMOUNTS =
      List.of("ACRDLIM", "ACSHLIM", "ACURBAL", "ACRCYCR", "ACRCYDB");

  /** 3300-SETUP-SCREEN-ATTRS cursor priority. */
  private static final List<String> CURSOR_ORDER =
      List.of(
          "ACSTTUS", "OPNYEAR", "OPNMON", "OPNDAY", "ACRDLIM", "EXPYEAR", "EXPMON", "EXPDAY",
          "ACSHLIM", "RISYEAR", "RISMON", "RISDAY", "ACURBAL", "ACRCYCR", "ACRCYDB", "ACTSSN1",
          "ACTSSN2", "ACTSSN3", "DOBYEAR", "DOBMON", "DOBDAY", "ACSTFCO", "ACSFNAM", "ACSMNAM",
          "ACSLNAM", "ACSADL1", "ACSSTTE", "ACSZIPC", "ACSCITY", "ACSCTRY", "ACSPH1A", "ACSPH1B",
          "ACSPH1C", "ACSPH2A", "ACSPH2B", "ACSPH2C", "ACSEFTC", "ACSPFLG");

  static {
    for (java.lang.reflect.RecordComponent rc : CoactupScreen.Input.class.getRecordComponents()) {
      LENGTHS.put(
          rc.getAccessor()
              .getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class)
              .value(),
          rc.getAnnotation(com.carddemo.contracts.screen.ScreenField.class).length());
    }
  }

  private final AccountLookup lookup;
  private final AccountUpdateWriter writer;
  private final BusinessClock clock;

  public CoactupcService(AccountLookup lookup, AccountUpdateWriter writer, BusinessClock clock) {
    this.lookup = lookup;
    this.writer = writer;
    this.clock = clock;
  }

  /** Working storage of one task. */
  private static final class Task {
    CommArea ca;
    ChangeAction action;
    AccountSnapshot old;
    Map<String, String> nw = new HashMap<>();
    Map<String, BigDecimal> amounts = new HashMap<>();
    String ccAcctId;
    String filter = "BLANK";
    String returnMsg;
    boolean inputError;
    boolean noChangesDetected;
    final Set<String> errors = new java.util.LinkedHashSet<>();
    final Set<String> blanks = new java.util.HashSet<>();

    void msg(String m) {
      inputError = true;
      if (returnMsg == null) {
        returnMsg = m;
      }
    }

    void error(String field, String m) {
      errors.add(field);
      msg(m);
    }

    void blank(String field, String m) {
      errors.add(field);
      blanks.add(field);
      msg(m);
    }

    String v(String field) {
      return nw.get(field);
    }
  }

  /**
   * One CAUP task.
   *
   * @param commarea COMMAREA received ({@code null} = {@code EIBCALEN = 0})
   * @param state program COMMAREA of the previous turn ({@code null} on first entry)
   */
  public ScreenOutcome<CoactupScreen.Output, State> process(
      ConversationContext commarea,
      State state,
      String userId,
      UserType userType,
      Action aid,
      CoactupScreen.Input input) {
    Task t = new Task();
    boolean init =
        commarea == null
            || (CoactvwcService.MENU_PGM.equals(CobolText.upperTrim(commarea.fromProgram()))
                && commarea.programContext() != ProgramContext.REENTER);
    if (init || state == null) {
      t.ca = init ? CommArea.initialized(userId, userType) : CommArea.of(commarea);
      t.action = ChangeAction.NOT_FETCHED;
    } else {
      t.ca = CommArea.of(commarea);
      t.action = state.action();
      t.old = state.old();
      t.nw = new HashMap<>(state.newFields());
      t.amounts = new HashMap<>(state.newAmounts());
    }
    boolean valid =
        aid == Action.ENTER
            || aid == Action.PF3
            || aid == Action.PF5 && t.action == ChangeAction.CHANGES_OK_NOT_CONFIRMED
            || aid == Action.PF12 && t.action != ChangeAction.NOT_FETCHED;
    Action key = valid ? aid : Action.ENTER;
    if (key == Action.PF3) {
      return exit(t.ca);
    }
    if (t.action == ChangeAction.NOT_FETCHED && !t.ca.isReenter()
        || CoactvwcService.MENU_PGM.equals(CobolText.upperTrim(t.ca.fromProgram))
            && !t.ca.isReenter()) {
      t.action = ChangeAction.NOT_FETCHED;
      t.old = null;
      return send(t, true);
    }
    if (t.action == ChangeAction.CHANGES_OKAYED_AND_DONE
        || t.action == ChangeAction.CHANGES_OKAYED_LOCK_ERROR
        || t.action == ChangeAction.CHANGES_OKAYED_BUT_FAILED) {
      Task fresh = new Task();
      fresh.ca = t.ca;
      fresh.ca.acctId = 0L;
      fresh.action = ChangeAction.NOT_FETCHED;
      fresh.ca.pgmContext = ProgramContext.ENTER;
      return send(fresh, true);
    }
    receive(t, input);
    editInputs(t);
    decide(t, key);
    return send(t, false);
  }

  private ScreenOutcome<CoactupScreen.Output, State> exit(CommArea ca) {
    ca.toTranid = CommArea.blank(ca.fromTranid) ? CoactvwcService.MENU_TRANID : ca.fromTranid;
    ca.toProgram = CommArea.blank(ca.fromProgram) ? CoactvwcService.MENU_PGM : ca.fromProgram;
    ca.fromTranid = THIS_TRANID;
    ca.fromProgram = THIS_PGM;
    ca.userType = UserType.USER;
    ca.pgmContext = ProgramContext.ENTER;
    ca.lastMapset = THIS_MAPSET;
    ca.lastMap = THIS_MAP;
    ConversationContext ctx = ca.toContext();
    return new ScreenOutcome<>(
        ScreenResponse.<CoactupScreen.Output>of(ScreenId.COACTUP, null, ctx)
            .withNextScreen(CoactvwcService.screenOf(ca.toProgram)),
        ctx,
        null);
  }

  /** 1100-RECEIVE-MAP. */
  private static void receive(Task t, CoactupScreen.Input input) {
    Map<String, String> in = ScreenFields.toMap(input);
    String acct = in.get("ACCTSID");
    t.ccAcctId = blankOrStar(acct) ? null : CobolText.pad(acct, 11);
    t.nw = new HashMap<>();
    t.amounts = new HashMap<>();
    t.nw.put("ACCTSID", t.ccAcctId);
    if (t.action == ChangeAction.NOT_FETCHED) {
      return;
    }
    for (Map.Entry<String, Integer> e : LENGTHS.entrySet()) {
      String name = e.getKey();
      if ("ACCTSID".equals(name)) {
        continue;
      }
      String raw = in.get(name);
      if (blankOrStar(raw)) {
        continue;
      }
      String v =
          RIGHT_JUSTIFIED.contains(name)
              ? CobolText.rightJustify(raw, e.getValue())
              : CobolText.pad(raw, e.getValue());
      t.nw.put(name, v);
      if (AMOUNTS.contains(name) && CobolText.testNumvalC(v)) {
        t.amounts.put(name, CobolText.toS9v2(CobolText.numvalC(v)));
      }
    }
  }

  private static boolean blankOrStar(String s) {
    return s == null || CobolText.isBlank(s) || "*".equals(s.strip()) && s.startsWith("*");
  }

  /** 1200-EDIT-MAP-INPUTS. */
  private void editInputs(Task t) {
    if (t.action == ChangeAction.NOT_FETCHED) {
      editAccount(t);
      if ("BLANK".equals(t.filter)) {
        t.returnMsg = NO_SEARCH_CRITERIA;
      }
      return;
    }
    t.filter = "VALID";
    if (!changed(t)) {
      t.noChangesDetected = true;
      if (t.returnMsg == null) {
        t.returnMsg = NO_CHANGES_DETECTED;
      }
      return;
    }
    if (t.action == ChangeAction.CHANGES_OK_NOT_CONFIRMED
        || t.action == ChangeAction.CHANGES_OKAYED_AND_DONE) {
      return;
    }
    t.action = ChangeAction.CHANGES_NOT_OK;
    FieldEdits e = new FieldEdits(t, clock);
    e.yesNo("Account Status", "ACSTTUS");
    e.date("Open Date", "OPN", false);
    e.signed9v2("Credit Limit", "ACRDLIM");
    e.date("Expiry Date", "EXP", false);
    e.signed9v2("Cash Credit Limit", "ACSHLIM");
    e.date("Reissue Date", "RIS", false);
    e.signed9v2("Current Balance", "ACURBAL");
    e.signed9v2("Current Cycle Credit Limit", "ACRCYCR");
    e.signed9v2("Current Cycle Debit Limit", "ACRCYDB");
    e.ssn();
    e.date("Date of Birth", "DOB", true);
    if (e.numRequired("FICO Score", "ACSTFCO", 3)) {
      int fico = Integer.parseInt(t.v("ACSTFCO").substring(0, 3));
      if (fico < 300 || fico > 850) {
        t.error("ACSTFCO", "FICO Score: should be between 300 and 850");
      }
    }
    e.alpha("First Name", "ACSFNAM", 25, true);
    e.alpha("Middle Name", "ACSMNAM", 25, false);
    e.alpha("Last Name", "ACSLNAM", 25, true);
    e.mandatory("Address Line 1", "ACSADL1", 50);
    boolean stateOk = e.alpha("State", "ACSSTTE", 2, true) && e.stateCode();
    boolean zipOk = e.numRequired("Zip", "ACSZIPC", 5);
    e.alpha("City", "ACSCITY", 50, true);
    e.alpha("Country", "ACSCTRY", 3, true);
    e.phone("Phone Number 1", "ACSPH1");
    e.phone("Phone Number 2", "ACSPH2");
    e.numRequired("EFT Account Id", "ACSEFTC", 10);
    e.yesNo("Primary Card Holder", "ACSPFLG");
    if (stateOk && zipOk) {
      e.stateZip();
    }
    if (!t.inputError) {
      t.action = ChangeAction.CHANGES_OK_NOT_CONFIRMED;
    }
  }

  /** 1210-EDIT-ACCOUNT. */
  private static void editAccount(Task t) {
    if (t.ccAcctId == null) {
      t.inputError = true;
      t.filter = "BLANK";
      t.ca.acctId = 0L;
      return;
    }
    if (!CobolText.isNumeric(t.ccAcctId) || Long.parseLong(t.ccAcctId) == 0) {
      t.msg(ACCT_INVALID);
      t.filter = "NOT_OK";
      t.ca.acctId = 0L;
      return;
    }
    t.ca.acctId = Long.parseLong(t.ccAcctId);
    t.filter = "VALID";
  }

  /** 1205-COMPARE-OLD-NEW: {@code true} = CHANGE-HAS-OCCURRED. */
  private static boolean changed(Task t) {
    if (t.old == null) {
      return true;
    }
    Map<String, String> o = t.old.fields();
    if (!same(t.ccAcctId, o.get("ACCTSID"))
        || !CobolText.upperTrim(raw(t.v("ACSTTUS"))).equals(CobolText.upperTrim(o.get("ACSTTUS")))
        || t.v("ACSTTUS") == null) {
      return true;
    }
    for (String a : AMOUNTS) {
      BigDecimal n = t.amounts.get(a);
      if (n == null || n.compareTo(t.old.amounts().get(a)) != 0) {
        return true;
      }
    }
    for (String f : List.of("OPN", "EXP", "RIS")) {
      for (String p : List.of("YEAR", "MON", "DAY")) {
        if (!same(t.v(f + p), o.get(f + p))) {
          return true;
        }
      }
    }
    if (!upperSame(t, o, "AADDGRP")) {
      return true;
    }
    for (String f :
        List.of(
            "ACSTNUM", "ACSFNAM", "ACSMNAM", "ACSLNAM", "ACSADL1", "ACSADL2", "ACSCITY", "ACSSTTE",
            "ACSCTRY", "ACSZIPC", "ACSGOVT", "ACSPFLG")) {
      if (!upperSame(t, o, f)) {
        return true;
      }
    }
    for (String f :
        List.of(
            "ACSPH1A", "ACSPH1B", "ACSPH1C", "ACSPH2A", "ACSPH2B", "ACSPH2C", "ACTSSN1", "ACTSSN2",
            "ACTSSN3", "DOBYEAR", "DOBMON", "DOBDAY", "ACSEFTC", "ACSTFCO")) {
      if (!same(t.v(f), o.get(f))) {
        return true;
      }
    }
    return false;
  }

  private static String raw(String s) {
    return s == null ? "" : s;
  }

  private static boolean same(String nw, String old) {
    return nw != null && old != null && nw.stripTrailing().equals(old.stripTrailing());
  }

  private static boolean upperSame(Task t, Map<String, String> o, String f) {
    return t.v(f) == null
        ? CobolText.isBlank(o.get(f))
        : CobolText.upperTrim(t.v(f)).equals(CobolText.upperTrim(o.get(f)));
  }

  /** 2000-DECIDE-ACTION. */
  private void decide(Task t, Action key) {
    if (t.action == ChangeAction.NOT_FETCHED || key == Action.PF12) {
      if ("VALID".equals(t.filter)) {
        t.returnMsg = null;
        t.old = null;
        AccountLookup.Result r = lookup.read(t.ca.acctId);
        if (r.xref() != null) {
          t.ca.custId = r.xref().getXrefCustId().longValue();
          String card = r.xref().getXrefCardNum().strip();
          t.ca.cardNum = CobolText.isNumeric(card) ? Long.valueOf(card) : null;
        }
        if (r.message() != null) {
          t.msg(r.message());
          if (r.acctFilterNotOk()) {
            t.filter = "NOT_OK";
          }
        }
        if (r.foundCustomer()) {
          // 9500-STORE-FETCHED-DATA
          t.ca.custFname = r.customer().getCustFirstName();
          t.ca.custMname = r.customer().getCustMiddleName();
          t.ca.custLname = r.customer().getCustLastName();
          t.ca.acctStatus = r.account().getAcctActiveStatus();
          t.old = AccountSnapshot.of(r.account(), r.customer());
          t.action = ChangeAction.SHOW_DETAILS;
          t.noChangesDetected = false;
          t.errors.clear();
          t.blanks.clear();
        }
      }
      return;
    }
    if (t.action == ChangeAction.CHANGES_OK_NOT_CONFIRMED && key == Action.PF5) {
      AccountUpdateWriter.Result r;
      try {
        r =
            writer.write(
                Long.parseLong(t.ccAcctId.strip()),
                t.ca.custId == null ? 0 : t.ca.custId.intValue(),
                t.old,
                t.nw,
                t.amounts);
      } catch (ObjectOptimisticLockingFailureException e) {
        r = AccountUpdateWriter.Result.DATA_CHANGED;
      } catch (RuntimeException e) {
        r = AccountUpdateWriter.Result.UPDATE_FAILED;
      }
      switch (r) {
        case COULD_NOT_LOCK_ACCT -> {
          t.msg(COULD_NOT_LOCK_ACCT);
          t.action = ChangeAction.CHANGES_OKAYED_LOCK_ERROR;
        }
        case UPDATE_FAILED -> {
          t.msg(UPDATE_FAILED);
          t.action = ChangeAction.CHANGES_OKAYED_BUT_FAILED;
        }
        case DATA_CHANGED -> {
          t.msg(DATA_CHANGED);
          t.action = ChangeAction.SHOW_DETAILS;
        }
        case COULD_NOT_LOCK_CUST -> {
          // COBOL: the lock error falls into WHEN OTHER of 2000-DECIDE-ACTION
          t.msg(COULD_NOT_LOCK_CUST);
          t.action = ChangeAction.CHANGES_OKAYED_AND_DONE;
        }
        default -> t.action = ChangeAction.CHANGES_OKAYED_AND_DONE;
      }
      if (t.action == ChangeAction.CHANGES_OKAYED_AND_DONE && CommArea.blank(t.ca.fromTranid)) {
        t.ca.acctId = 0L;
        t.ca.cardNum = 0L;
        t.ca.acctStatus = null;
      }
    }
  }

  /** 3000-SEND-MAP. */
  private ScreenOutcome<CoactupScreen.Output, State> send(Task t, boolean pgmEnter) {
    Map<String, String> out = new HashMap<>();
    if (!pgmEnter) {
      long acctN =
          t.ccAcctId != null && CobolText.isNumeric(t.ccAcctId) ? Long.parseLong(t.ccAcctId) : 0;
      out.put("ACCTSID", acctN == 0 && "VALID".equals(t.filter) ? null : t.ccAcctId);
      if (t.action == ChangeAction.NOT_FETCHED || acctN == 0) {
        // 3201-SHOW-INITIAL-VALUES
      } else if (t.action == ChangeAction.SHOW_DETAILS || !t.action.changesMade()) {
        showOriginal(t, out);
      } else {
        showUpdated(t, out);
      }
    }
    String info =
        switch (pgmEnter ? ChangeAction.NOT_FETCHED : t.action) {
          case NOT_FETCHED -> PROMPT_FOR_SEARCH_KEYS;
          case SHOW_DETAILS, CHANGES_NOT_OK -> PROMPT_FOR_CHANGES;
          case CHANGES_OK_NOT_CONFIRMED -> PROMPT_FOR_CONFIRMATION;
          case CHANGES_OKAYED_AND_DONE -> CONFIRM_UPDATE_SUCCESS;
          default -> INFORM_FAILURE;
        };
    boolean acctBad = "BLANK".equals(t.filter) || "NOT_OK".equals(t.filter);
    if (!pgmEnter && "BLANK".equals(t.filter)) {
      out.put("ACCTSID", "*");
    }
    if (!pgmEnter && t.action != ChangeAction.NOT_FETCHED && !acctBad) {
      for (String b : t.blanks) {
        out.put(b, "*");
      }
    }
    String cursor = "ACCTSID";
    if (t.noChangesDetected) {
      cursor = "ACSTTUS";
    } else if (!acctBad) {
      for (String f : CURSOR_ORDER) {
        if (t.errors.contains(f)) {
          cursor = f;
          break;
        }
      }
    }
    String errmsg = t.returnMsg == null ? null : CobolText.pad(t.returnMsg, 75).stripTrailing();
    out.put("ERRMSG", errmsg);
    out.put("INFOMSG", info);
    out.put("TRNNAME", THIS_TRANID);
    out.put("PGMNAME", THIS_PGM);
    ScreenHeader h = ScreenHeader.of(clock);
    out.put("TITLE01", h.title01());
    out.put("TITLE02", h.title02());
    out.put("CURDATE", h.curdate());
    out.put("CURTIME", h.curtime());
    out.put("FKEYS", "ENTER=Process F3=Exit");
    boolean confirm = !pgmEnter && t.action == ChangeAction.CHANGES_OK_NOT_CONFIRMED;
    out.put("FKEY05", confirm ? "F5=Save" : null);
    boolean f12 =
        !pgmEnter && t.action.changesMade() && t.action != ChangeAction.CHANGES_OKAYED_AND_DONE;
    out.put("FKEY12", f12 || confirm ? "F12=Cancel" : null);
    CoactupScreen.Output o = ScreenFields.fromMap(CoactupScreen.Output.class, out);

    t.ca.pgmContext = ProgramContext.REENTER;
    ConversationContext ctx = t.ca.toContext();
    List<ScreenMessage> messages = new ArrayList<>();
    List<FieldError> errors = new ArrayList<>();
    if (errmsg != null) {
      messages.add(ScreenMessage.error(errmsg));
      errors.add(new FieldError(cursor, errmsg));
    }
    messages.add(ScreenMessage.info(info));
    State st =
        new State(
            pgmEnter ? ChangeAction.NOT_FETCHED : t.action,
            pgmEnter ? null : t.old,
            Map.copyOf(withoutNulls(t.nw)),
            Map.copyOf(t.amounts),
            ctx,
            Map.copyOf(withoutNulls(ScreenFields.toMap(o))));
    return new ScreenOutcome<>(
        new ScreenResponse<>(ScreenId.COACTUP, o, messages, null, errors, ctx), ctx, st);
  }

  private static Map<String, String> withoutNulls(Map<String, String> m) {
    Map<String, String> r = new HashMap<>();
    m.forEach(
        (k, v) -> {
          if (v != null) {
            r.put(k, v);
          }
        });
    return r;
  }

  /** 3202-SHOW-ORIGINAL-VALUES. */
  private static void showOriginal(Task t, Map<String, String> out) {
    if (t.old == null) {
      return;
    }
    t.old.fields().forEach((k, v) -> out.putIfAbsent(k, v));
    out.put("ACCTSID", out.get("ACCTSID") == null ? null : t.ccAcctId);
    t.old.amounts().forEach((k, v) -> out.put(k, CobolText.formatAmount(v)));
  }

  /** 3203-SHOW-UPDATED-VALUES. */
  private static void showUpdated(Task t, Map<String, String> out) {
    t.nw.forEach(
        (k, v) -> {
          if (!"ACCTSID".equals(k)) {
            out.put(k, v);
          }
        });
    for (String a : AMOUNTS) {
      BigDecimal n = t.amounts.get(a);
      if (n != null && !t.errors.contains(a)) {
        out.put(a, CobolText.formatAmount(n));
      }
    }
  }

  /** Field edit paragraphs 1215..1280 and CSUTLDPY; first message wins, every flag is kept. */
  private static final class FieldEdits {
    private final Task t;
    private final BusinessClock clock;

    FieldEdits(Task t, BusinessClock clock) {
      this.t = t;
      this.clock = clock;
    }

    private static boolean blank(String v) {
      return v == null || CobolText.isBlank(v);
    }

    /** 1220-EDIT-YESNO. */
    boolean yesNo(String name, String f) {
      String v = t.v(f);
      if (blank(v) || "0".equals(v)) {
        t.blank(f, name + " must be supplied.");
        return false;
      }
      if (!"Y".equals(v) && !"N".equals(v)) {
        t.error(f, name + " must be Y or N.");
        return false;
      }
      return true;
    }

    /** 1215-EDIT-MANDATORY. */
    boolean mandatory(String name, String f, int len) {
      if (blank(t.v(f))) {
        t.blank(f, name + " must be supplied.");
        return false;
      }
      return true;
    }

    /** 1225-EDIT-ALPHA-REQD / 1235-EDIT-ALPHA-OPT: letters and spaces only. */
    boolean alpha(String name, String f, int len, boolean required) {
      String v = t.v(f);
      if (blank(v)) {
        if (required) {
          t.blank(f, name + " must be supplied.");
          return false;
        }
        return true;
      }
      String s = CobolText.pad(v, len);
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (!(c == ' ' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
          t.error(f, name + " can have alphabets only.");
          return false;
        }
      }
      return true;
    }

    /** 1245-EDIT-NUM-REQD. */
    boolean numRequired(String name, String f, int len) {
      return numRequired(name, f, len, t.v(f));
    }

    private boolean numRequired(String name, String f, int len, String v) {
      if (blank(v)) {
        t.blank(f, name + " must be supplied.");
        return false;
      }
      String s = CobolText.pad(v, len);
      if (!CobolText.isNumeric(s)) {
        t.error(f, name + " must be all numeric.");
        return false;
      }
      if (new BigDecimal(s).signum() == 0) {
        t.error(f, name + " must not be zero.");
        return false;
      }
      return true;
    }

    /** 1250-EDIT-SIGNED-9V2. */
    boolean signed9v2(String name, String f) {
      String v = t.v(f);
      if (blank(v)) {
        t.blank(f, name + " must be supplied.");
        return false;
      }
      if (!CobolText.testNumvalC(v)) {
        t.error(f, name + " is not valid");
        return false;
      }
      return true;
    }

    /** 1265-EDIT-US-SSN. */
    void ssn() {
      if (numRequired("SSN: First 3 chars", "ACTSSN1", 3)) {
        int p1 = Integer.parseInt(t.v("ACTSSN1").substring(0, 3));
        if (p1 == 0 || p1 == 666 || p1 >= 900) {
          t.error("ACTSSN1", "SSN: First 3 chars: should not be 000, 666, or between 900 and 999");
        }
      }
      numRequired("SSN 4th & 5th chars", "ACTSSN2", 2);
      numRequired("SSN Last 4 chars", "ACTSSN3", 4);
    }

    /** 1270-EDIT-US-STATE-CD. */
    boolean stateCode() {
      if (!com.carddemo.online.account.support.LookupCodes.isUsStateCode(t.v("ACSSTTE"))) {
        t.error("ACSSTTE", "State: is not a valid state code");
        return false;
      }
      return true;
    }

    /** 1280-EDIT-US-STATE-ZIP-CD. */
    void stateZip() {
      String key = t.v("ACSSTTE") + CobolText.pad(t.v("ACSZIPC"), 2).substring(0, 2);
      if (!com.carddemo.online.account.support.LookupCodes.isUsStateZip2Combo(key)) {
        t.errors.add("ACSSTTE");
        t.error("ACSZIPC", "Invalid zip code for state");
      }
    }

    /** 1260-EDIT-US-PHONE-NUM: optional when all three parts are blank. */
    void phone(String name, String prefix) {
      String a = t.v(prefix + "A");
      String b = t.v(prefix + "B");
      String c = t.v(prefix + "C");
      if (blank(a) && blank(b) && blank(c)) {
        return;
      }
      part(name, prefix + "A", a, "Area code", 3, true);
      part(name, prefix + "B", b, "Prefix code", 3, false);
      part(name, prefix + "C", c, "Line number code", 4, false);
    }

    private void part(String name, String f, String v, String what, int len, boolean area) {
      if (blank(v)) {
        t.blank(f, name + ": " + what + " must be supplied.");
        return;
      }
      if (!CobolText.isNumeric(v)) {
        t.error(f, name + ": " + what + " must be A " + len + " digit number.");
        return;
      }
      if (Integer.parseInt(v) == 0) {
        t.error(f, name + ": " + what + " cannot be zero");
        return;
      }
      if (area
          && !com.carddemo.online.account.support.LookupCodes.isGeneralPurposeAreaCode(v.strip())) {
        t.error(f, name + ": Not valid North America general purpose area code");
      }
    }

    /** {@code PIC 9(n)} value of a right-justified field (leading blanks read as zeros). */
    private static int zonedValue(String v) {
      if (v == null) {
        return -1;
      }
      String s = v.replace(' ', '0');
      return CobolText.isNumeric(s) ? Integer.parseInt(s) : -1;
    }

    /** EDIT-DATE-CCYYMMDD (+ EDIT-DATE-OF-BIRTH when {@code dob}). */
    void date(String name, String prefix, boolean dob) {
      String yf = prefix + "YEAR";
      String mf = prefix + "MON";
      String df = prefix + "DAY";
      String y = t.v(yf);
      String m = t.v(mf);
      String d = t.v(df);
      boolean ok = true;
      if (blank(y)) {
        t.blank(yf, name + " : Year must be supplied.");
        ok = false;
      } else if (!CobolText.isNumeric(y)) {
        t.error(yf, name + " must be 4 digit number.");
        ok = false;
      } else if (!y.startsWith("19") && !y.startsWith("20")) {
        t.error(yf, name + " : Century is not valid.");
        ok = false;
      }
      int mm = zonedValue(m);
      if (blank(m)) {
        t.blank(mf, name + " : Month must be supplied.");
        ok = false;
      } else if (mm < 1 || mm > 12 || !CobolText.testNumval(m)) {
        t.error(mf, name + ": Month must be a number between 1 and 12.");
        ok = false;
      }
      int dd = zonedValue(d);
      if (blank(d)) {
        t.blank(df, name + " : Day must be supplied.");
        ok = false;
      } else if (!CobolText.testNumval(d) || dd < 1 || dd > 31) {
        t.error(df, name + ":day must be a number between 1 and 31.");
        ok = false;
      }
      // EDIT-DAY-MONTH-YEAR
      if (!Set.of(1, 3, 5, 7, 8, 10, 12).contains(mm) && dd == 31) {
        t.errors.add(mf);
        t.error(df, name + ":Cannot have 31 days in this month.");
        return;
      }
      if (mm == 2 && dd == 30) {
        t.errors.add(mf);
        t.error(df, name + ":Cannot have 30 days in this month.");
        return;
      }
      if (mm == 2 && dd == 29) {
        int year = zonedValue(y);
        int yy = year < 0 ? 0 : year % 100;
        int rem = year < 0 ? 0 : year % (yy == 0 ? 400 : 4);
        if (rem != 0) {
          t.errors.add(mf);
          t.errors.add(yf);
          t.error(df, name + ":Not a leap year.Cannot have 29 days in this month.");
          return;
        }
      }
      if (!ok) {
        return;
      }
      // EDIT-DATE-LE (CSUTLDTC/CEEDAYS)
      java.time.LocalDate date;
      try {
        date = java.time.LocalDate.of(Integer.parseInt(y), mm, dd);
      } catch (java.time.DateTimeException e) {
        t.errors.add(yf);
        t.errors.add(mf);
        t.error(df, name + " validation error Sev code: 0003 Message code: 2513");
        return;
      }
      if (dob && !clock.today().isAfter(date)) {
        t.errors.add(yf);
        t.errors.add(mf);
        t.error(df, name + ":cannot be in the future ");
      }
    }
  }
}
