package com.carddemo.ext.authorization.web;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Copau01Screen;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.service.AuthFraudService;
import com.carddemo.ext.authorization.store.AuthKey;
import com.carddemo.ext.authorization.store.PendingAuthDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COPAUS1C (CPVD): authorization detail, PF5 fraud toggle (COPAUS2C), PF8 next. */
@Service
@ConditionalOnAuthorizationExtension
public class CpvdScreenService {

  static final String MSG_LAST = "Already at the last Authorization...";
  private static final Map<String, String> REASONS =
      Map.of(
          "0000", "APPROVED",
          "3100", "INVALID CARD",
          "4100", "INSUFFICNT FUND",
          "4200", "CARD NOT ACTIVE",
          "4300", "ACCOUNT CLOSED",
          "4400", "EXCED DAILY LMT",
          "5100", "CARD FRAUD",
          "5200", "MERCHANT FRAUD",
          "5300", "LOST CARD",
          "9000", "UNKNOWN");

  private final AuthViewStateStore store;
  private final PendingAuthDatabase db;
  private final AuthFraudService fraud;
  private final BusinessClock clock;
  private final ObjectMapper json = new ObjectMapper();

  public CpvdScreenService(
      AuthViewStateStore store,
      PendingAuthDatabase db,
      AuthFraudService fraud,
      BusinessClock clock) {
    this.store = store;
    this.db = db;
    this.fraud = fraud;
    this.clock = clock;
  }

  @Transactional
  public ScreenResponse<Copau01Screen.Output> first(String user) {
    return receive(user, Action.ENTER);
  }

  @Transactional
  public ScreenResponse<Copau01Screen.Output> receive(String user, Action action) {
    AuthViewState st = store.get(user);
    Map<String, String> out = new LinkedHashMap<>();
    String msg = "";
    ScreenId next = null;
    switch (action) {
      case PF3 -> next = ScreenId.COPAU00;
      case PF5 -> {
        if (valid(st)) {
          msg =
              fraud
                  .toggle(
                      st.acctId, st.custId == null ? 0 : st.custId, AuthKey.fromHex(st.selected))
                  .orElse("");
        }
        populate(st, out);
      }
      case PF8 -> {
        Optional<PendingAuthDetail> n =
            valid(st) ? db.nextChild(st.acctId, AuthKey.fromHex(st.selected)) : Optional.empty();
        if (n.isEmpty()) {
          msg = MSG_LAST;
        } else {
          st.selected = PendingAuthDatabase.keyOf(n.get()).toHex();
        }
        populate(st, out);
      }
      case ENTER -> populate(st, out);
      default -> {
        populate(st, out);
        msg = ScreenFormat.INVALID_KEY;
      }
    }
    out.put("TRNNAME", "CPVD");
    out.put("PGMNAME", "COPAUS1C");
    out.put("TITLE01", ScreenFormat.TITLE01);
    out.put("TITLE02", ScreenFormat.TITLE02);
    out.put("CURDATE", ScreenFormat.curdate(clock));
    out.put("CURTIME", ScreenFormat.curtime(clock));
    out.put("ERRMSG", msg);
    ScreenResponse<Copau01Screen.Output> r =
        ScreenResponse.of(
            ScreenId.COPAU01, json.convertValue(out, Copau01Screen.Output.class), null);
    if (!msg.isEmpty()) {
      r = r.withMessage(ScreenMessage.info(msg));
    }
    return next != null ? r.withNextScreen(next) : r;
  }

  private static boolean valid(AuthViewState st) {
    return st.acctId != null && st.selected != null && !st.selected.isBlank();
  }

  private void populate(AuthViewState st, Map<String, String> out) {
    if (!valid(st)) {
      return;
    }
    Optional<PendingAuthDetail> found = db.findFirstChild(st.acctId, AuthKey.fromHex(st.selected));
    if (found.isEmpty()) {
      return;
    }
    PendingAuthDetail d = found.get();
    out.put("CARDNUM", d.getCardNum());
    out.put("AUTHDT", ScreenFormat.date(d.getAuthOrigDate()));
    out.put("AUTHTM", ScreenFormat.time(d.getAuthOrigTime()));
    out.put("AUTHAMT", ScreenFormat.amount(d.getApprovedAmt(), 8));
    out.put("AUTHRSP", "00".equals(d.getAuthRespCode()) ? "A" : "D");
    String desc = REASONS.get(d.getAuthRespReason());
    out.put("AUTHRSN", desc == null ? "9999-ERROR" : d.getAuthRespReason() + '-' + desc);
    out.put("AUTHCD", String.format("%06d", d.getProcessingCode()));
    out.put("POSEMD", String.format("%02d", d.getPosEntryMode()));
    out.put("AUTHSRC", d.getMessageSource());
    out.put("MCCCD", d.getMerchantCategoryCode());
    String exp = AuthorizationScreenSupport.padRight(d.getCardExpiryDate(), 4);
    out.put("CRDEXP", exp.substring(0, 2) + '/' + exp.substring(2, 4));
    out.put("AUTHTYP", d.getAuthType());
    out.put("TRNID", d.getTransactionId());
    out.put("AUTHMTC", d.getMatchStatus());
    String f = d.getAuthFraud();
    out.put("AUTHFRD", "F".equals(f) || "R".equals(f) ? f + '-' + d.getFraudRptDate() : "-");
    out.put("MERNAME", d.getMerchantName());
    out.put("MERID", d.getMerchantId());
    out.put("MERCITY", d.getMerchantCity());
    out.put("MERST", d.getMerchantState());
    out.put("MERZIP", d.getMerchantZip());
  }
}
