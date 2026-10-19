package com.carddemo.ext.authorization.web;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Copau00Screen;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.entity.Customer;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.model.PendingAuthSummary;
import com.carddemo.ext.authorization.store.PendingAuthDatabase;
import com.carddemo.ext.authorization.store.PendingAuthDetailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COPAUS0C (CPVS): pending authorization summary list, 5 rows per page, PF7/PF8 paging. */
@Service
@ConditionalOnAuthorizationExtension
public class CpvsScreenService {

  static final String MSG_ACCT_REQUIRED = "Please enter Acct Id...";
  static final String MSG_ACCT_NUMERIC = "Acct Id must be Numeric ...";
  static final String MSG_INVALID_SEL = "Invalid selection. Valid value is S";
  static final String MSG_TOP = "You are already at the top of the page...";
  static final String MSG_BOTTOM = "You are already at the bottom of the page...";

  private final AuthViewStateStore store;
  private final CardXrefRepository xrefs;
  private final AccountRepository accounts;
  private final CustomerRepository customers;
  private final PendingAuthDatabase db;
  private final PendingAuthDetailRepository details;
  private final BusinessClock clock;
  private final ObjectMapper json = new ObjectMapper();

  public CpvsScreenService(
      AuthViewStateStore store,
      CardXrefRepository xrefs,
      AccountRepository accounts,
      CustomerRepository customers,
      PendingAuthDatabase db,
      PendingAuthDetailRepository details,
      BusinessClock clock) {
    this.store = store;
    this.xrefs = xrefs;
    this.accounts = accounts;
    this.customers = customers;
    this.db = db;
    this.details = details;
    this.clock = clock;
  }

  private final class Ctx {
    final AuthViewState st;
    final Map<String, String> out = new LinkedHashMap<>();
    String msg = "";
    ScreenId next;
    String errorField;
    boolean summaryFound;

    Ctx(AuthViewState st) {
      this.st = st;
    }
  }

  @Transactional(readOnly = true)
  public ScreenResponse<Copau00Screen.Output> first(String user) {
    Ctx c = new Ctx(store.get(user));
    if (c.st.acctId != null) {
      c.out.put("ACCTID", String.format("%011d", c.st.acctId));
      gather(c);
    }
    return respond(c);
  }

  @Transactional(readOnly = true)
  public ScreenResponse<Copau00Screen.Output> receive(
      String user, Action action, Copau00Screen.Input in) {
    Ctx c = new Ctx(store.get(user));
    Copau00Screen.Input f =
        in == null ? new Copau00Screen.Input(null, null, null, null, null, null) : in;
    switch (action) {
      case ENTER -> enter(c, f);
      case PF3 -> c.next = ScreenId.COMEN01;
      case PF7 -> pf7(c);
      case PF8 -> pf8(c);
      default -> {
        c.msg = ScreenFormat.INVALID_KEY;
        c.errorField = "ACCTID";
      }
    }
    if (c.st.acctId != null && !c.out.containsKey("ACCTID")) {
      c.out.put("ACCTID", String.format("%011d", c.st.acctId));
    }
    return respond(c);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank() || s.chars().allMatch(ch -> ch == 0);
  }

  private void enter(Ctx c, Copau00Screen.Input f) {
    String acct = f.acctid();
    if (blank(acct)) {
      c.st.acctId = null;
      c.msg = MSG_ACCT_REQUIRED;
      c.errorField = "ACCTID";
      c.out.put("ACCTID", "");
      return;
    }
    String a = AuthorizationScreenSupport.padRight(acct, 11);
    if (!a.chars().allMatch(Character::isDigit)) {
      c.st.acctId = null;
      c.msg = MSG_ACCT_NUMERIC;
      c.errorField = "ACCTID";
      c.out.put("ACCTID", acct);
      return;
    }
    c.st.acctId = Long.parseLong(a);
    c.out.put("ACCTID", a);
    String[] sels = {f.sel0001(), f.sel0002(), f.sel0003(), f.sel0004(), f.sel0005()};
    String flag = null;
    String key = null;
    for (int i = 0; i < 5; i++) {
      if (!blank(sels[i])) {
        flag = sels[i].substring(0, 1);
        key = c.st.authKeys[i];
        break;
      }
    }
    if (flag != null && !blank(key)) {
      if ("S".equalsIgnoreCase(flag)) {
        c.st.selected = key;
        c.next = ScreenId.COPAU01;
        return;
      }
      c.msg = MSG_INVALID_SEL;
      c.errorField = "ACCTID";
    }
    gather(c);
  }

  private void gather(Ctx c) {
    c.st.pageNum = 0;
    accountDetails(c);
    if (c.summaryFound) {
      forward(c, 0);
    }
  }

  private void pf7(Ctx c) {
    if (c.st.pageNum > 1 && c.st.acctId != null) {
      c.st.pageNum--;
      String key = c.st.prevPg[c.st.pageNum];
      accountDetails(c);
      c.st.nextPage = true;
      forward(c, indexOf(c, key));
    } else {
      c.msg = MSG_TOP;
      if (c.st.acctId != null) {
        accountDetails(c);
        keepRowsOnScreen(c);
      }
    }
  }

  private void pf8(Ctx c) {
    int start = 0;
    if (!blank(c.st.last) && c.st.acctId != null) {
      accountDetails(c);
      start = indexOf(c, c.st.last) + 1;
    }
    if (c.st.nextPage && c.st.acctId != null) {
      forward(c, start);
    } else {
      c.msg = MSG_BOTTOM;
    }
  }

  private List<PendingAuthDetail> children(Ctx c) {
    return details.findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc(c.st.acctId);
  }

  private int indexOf(Ctx c, String hexKey) {
    List<PendingAuthDetail> all = children(c);
    for (int i = 0; i < all.size(); i++) {
      if (PendingAuthDatabase.keyOf(all.get(i)).toHex().equals(hexKey)) {
        return i;
      }
    }
    return all.size();
  }

  /** PROCESS-PAGE-FORWARD from {@code start}: up to 5 rows plus a look-ahead read. */
  private void forward(Ctx c, int start) {
    List<PendingAuthDetail> all = children(c);
    List<PendingAuthDetail> page =
        all.subList(Math.min(start, all.size()), Math.min(start + 5, all.size()));
    c.st.last = null;
    for (int i = 0; i < page.size(); i++) {
      String key = PendingAuthDatabase.keyOf(page.get(i)).toHex();
      c.st.last = key;
      if (i == 0) {
        c.st.pageNum++;
        if (c.st.pageNum < c.st.prevPg.length) {
          c.st.prevPg[c.st.pageNum] = key;
        }
      }
    }
    if (page.size() == 5) {
      c.st.nextPage = start + 5 < all.size();
    }
    rows(c, page);
  }

  /** SEND-ERASE-NO without repaint: the 3270 keeps the previous rows and their attributes. */
  private void keepRowsOnScreen(Ctx c) {
    String[] keys = c.st.authKeys.clone();
    rows(c, List.of());
    System.arraycopy(keys, 0, c.st.authKeys, 0, keys.length);
  }

  private void rows(Ctx c, List<PendingAuthDetail> page) {
    for (int i = 0; i < 5; i++) {
      String n = String.valueOf(i + 1);
      if (i < page.size()) {
        PendingAuthDetail d = page.get(i);
        c.st.authKeys[i] = PendingAuthDatabase.keyOf(d).toHex();
        c.out.put("TRNID0" + n, d.getTransactionId());
        c.out.put("PDATE0" + n, ScreenFormat.date(d.getAuthOrigDate()));
        c.out.put("PTIME0" + n, ScreenFormat.time(d.getAuthOrigTime()));
        c.out.put("PTYPE0" + n, d.getAuthType());
        c.out.put("PAPRV0" + n, "00".equals(d.getAuthRespCode()) ? "A" : "D");
        c.out.put("PSTAT0" + n, d.getMatchStatus());
        c.out.put("PAMT00" + n, ScreenFormat.amount(d.getApprovedAmt(), 8));
      } else {
        c.st.authKeys[i] = null;
        for (String p : List.of("TRNID0", "PDATE0", "PTIME0", "PTYPE0", "PAPRV0", "PSTAT0")) {
          c.out.put(p + n, "");
        }
        c.out.put("PAMT00" + n, "");
      }
    }
  }

  private void accountDetails(Ctx c) {
    List<CardXref> x = xrefs.findByXrefAcctId(c.st.acctId);
    if (x.isEmpty()) {
      c.msg =
          "Account:"
              + String.format("%011d", c.st.acctId)
              + " not found in XREF file. Resp:000000013 Reas:000000080";
      c.errorField = "ACCTID";
      return;
    }
    CardXref xref = x.get(0);
    c.st.custId = xref.getXrefCustId().longValue();
    Optional<Account> acct = accounts.findById(xref.getXrefAcctId());
    Optional<Customer> cust = customers.findById(xref.getXrefCustId());
    c.out.put("CUSTID", String.format("%09d", xref.getXrefCustId()));
    cust.ifPresent(
        cu -> {
          c.out.put(
              "CNAME",
              AuthorizationScreenSupport.name(
                  cu.getCustFirstName(), cu.getCustMiddleName(), cu.getCustLastName()));
          c.out.put(
              "ADDR001",
              AuthorizationScreenSupport.cut(
                  AuthorizationScreenSupport.untilDoubleSpace(cu.getCustAddrLine1())
                      + ','
                      + AuthorizationScreenSupport.untilDoubleSpace(cu.getCustAddrLine2()),
                  25));
          c.out.put(
              "ADDR002",
              AuthorizationScreenSupport.cut(
                  AuthorizationScreenSupport.untilDoubleSpace(cu.getCustAddrLine3())
                      + ','
                      + AuthorizationScreenSupport.padRight(cu.getCustAddrStateCd(), 2)
                      + ','
                      + AuthorizationScreenSupport.padRight(cu.getCustAddrZip(), 5).substring(0, 5),
                  25));
          c.out.put("PHONE1", AuthorizationScreenSupport.cut(cu.getCustPhoneNum1(), 13));
        });
    if (acct.isEmpty()) {
      c.msg =
          "Account:"
              + String.format("%011d", c.st.acctId)
              + " not found in ACCT file. Resp:000000013 Reas:000000080";
    }
    acct.ifPresent(
        a -> {
          c.out.put("CREDLIM", ScreenFormat.amount(a.getAcctCreditLimit(), 8));
          c.out.put("CASHLIM", ScreenFormat.amount(a.getAcctCashCreditLimit(), 5));
          c.out.put("ACCSTAT", AuthorizationScreenSupport.cut(a.getAcctActiveStatus(), 1));
        });
    Optional<PendingAuthSummary> s = db.findFirst(c.st.acctId);
    c.summaryFound = s.isPresent();
    if (s.isPresent()) {
      PendingAuthSummary p = s.get();
      c.out.put("APPRCNT", String.format("%03d", Math.abs(p.getApprovedAuthCnt()) % 1000));
      c.out.put("DECLCNT", String.format("%03d", Math.abs(p.getDeclinedAuthCnt()) % 1000));
      c.out.put("CREDBAL", ScreenFormat.amount(p.getCreditBalance(), 8));
      c.out.put("CASHBAL", ScreenFormat.amount(p.getCashBalance(), 5));
      c.out.put("APPRAMT", ScreenFormat.amount(p.getApprovedAuthAmt(), 5));
      c.out.put("DECLAMT", ScreenFormat.amount(p.getDeclinedAuthAmt(), 5));
    } else {
      c.out.put("APPRCNT", "000");
      c.out.put("DECLCNT", "000");
      c.out.put("CREDBAL", "0".repeat(12));
      c.out.put("CASHBAL", "0".repeat(9));
      c.out.put("APPRAMT", "0".repeat(10));
      c.out.put("DECLAMT", "0".repeat(10));
    }
  }

  private ScreenResponse<Copau00Screen.Output> respond(Ctx c) {
    c.out.put("TRNNAME", "CPVS");
    c.out.put("PGMNAME", "COPAUS0C");
    c.out.put("TITLE01", ScreenFormat.TITLE01);
    c.out.put("TITLE02", ScreenFormat.TITLE02);
    c.out.put("CURDATE", ScreenFormat.curdate(clock));
    c.out.put("CURTIME", ScreenFormat.curtime(clock));
    c.out.put("ERRMSG", c.msg);
    Copau00Screen.Output data = json.convertValue(c.out, Copau00Screen.Output.class);
    ScreenResponse<Copau00Screen.Output> r = ScreenResponse.of(ScreenId.COPAU00, data, null);
    if (!c.msg.isEmpty()) {
      r =
          r.withMessage(
              c.errorField != null ? ScreenMessage.error(c.msg) : ScreenMessage.info(c.msg));
    }
    if (c.errorField != null
        && (c.msg.equals(MSG_ACCT_REQUIRED) || c.msg.equals(MSG_ACCT_NUMERIC))) {
      r = r.withFieldError(new FieldError(c.errorField, c.msg));
    }
    return c.next != null ? r.withNextScreen(c.next) : r;
  }
}
