package com.carddemo.online.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Cobil00Screen;
import com.carddemo.online.transaction.file.Resp;
import com.carddemo.platform.time.FixedBusinessClock;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Cobil00cServiceTest {

  private final Fakes.Transact file = new Fakes.Transact().with(4);
  private final Fakes.Accounts accounts = new Fakes.Accounts().with(1, "194.00").with(2, "0.00");
  private final Fakes.Xref xref = new Fakes.Xref().with("4111111111111111", 1);
  private final Cobil00cService svc =
      new Cobil00cService(accounts, xref, file, FixedBusinessClock.golden());

  private ProgramResult<Cobil00Screen.Output> post(Action aid, String acct, String confirm) {
    var p = svc.run(Ctx.fromMenu("CB00", "COBIL00C"), CommareaExtension.initial(), null, Map.of());
    return svc.run(
        p.response().context(), p.extension(), aid, Map.of("ACTIDIN", acct, "CONFIRM", confirm));
  }

  @Test
  void showsBalanceAndAsksConfirmation() {
    var r = post(Action.ENTER, "00000000001", "");
    assertThat(Ctx.message(r.response())).isEqualTo("Confirm to make a bill payment...");
    assertThat(r.response().data().curbal()).isEqualTo("+0000000194.00");
    assertThat(r.response().fieldErrors()).isEmpty();
  }

  @Test
  void paysTheWholeBalance() {
    var r = post(Action.ENTER, "00000000001", "Y");
    assertThat(Ctx.message(r.response()))
        .isEqualTo("Payment successful.  Your Transaction ID is 0000000000000005.");
    assertThat(Ctx.green(r.response())).isTrue();
    var t = file.rows.get(Fakes.id(5));
    assertThat(t.getTranTypeCd()).isEqualTo("02");
    assertThat(t.getTranCatCd()).isEqualTo(2);
    assertThat(t.getTranAmt()).isEqualByComparingTo("194.00");
    assertThat(t.getTranCardNum()).isEqualTo("4111111111111111");
    assertThat(t.getTranOrigTs()).isEqualTo("2022-07-18 12:00:00.000000");
    assertThat(t.getTranMerchantId()).isEqualTo(999999999);
    assertThat(accounts.rows.get(1L).getAcctCurrBal()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(r.response().data().actidin()).isEmpty();
  }

  @Test
  void errors() {
    assertThat(Ctx.message(post(Action.ENTER, "", "").response()))
        .isEqualTo("Acct ID can NOT be empty...");
    assertThat(Ctx.message(post(Action.ENTER, "00000000009", "").response()))
        .isEqualTo("Account ID NOT found...");
    assertThat(Ctx.message(post(Action.ENTER, "0000000000A", "").response()))
        .isEqualTo("Account ID NOT found...");
    assertThat(Ctx.message(post(Action.ENTER, "00000000002", "Y").response()))
        .isEqualTo("You have nothing to pay...");
    assertThat(Ctx.message(post(Action.ENTER, "00000000001", "Z").response()))
        .isEqualTo("Invalid value. Valid values are (Y/N)...");
    var n = post(Action.ENTER, "00000000001", "N");
    assertThat(n.response().messages()).isEmpty();
    assertThat(n.response().data().actidin()).isEmpty();
    accounts.readResp = Resp.ERROR;
    assertThat(Ctx.message(post(Action.ENTER, "00000000001", "").response()))
        .isEqualTo("Unable to lookup Account...");
  }

  @Test
  void writeAndRewriteFailures() {
    accounts.rewriteResp = Resp.ERROR;
    assertThat(Ctx.message(post(Action.ENTER, "00000000001", "Y").response()))
        .isEqualTo("Unable to Update Account...");
    accounts.rewriteResp = Resp.NOTFND;
    accounts.with(1, "10.00");
    assertThat(Ctx.message(post(Action.ENTER, "00000000001", "Y").response()))
        .isEqualTo("Account ID NOT found...");
    accounts.rewriteResp = Resp.NORMAL;
    accounts.with(1, "10.00");
    file.writeResp = Resp.DUPREC;
    assertThat(Ctx.message(post(Action.ENTER, "00000000001", "Y").response()))
        .isEqualTo("Tran ID already exist...");
    file.writeResp = Resp.ERROR;
    assertThat(Ctx.message(post(Action.ENTER, "00000000001", "Y").response()))
        .isEqualTo("Unable to Add Bill pay Transaction...");
    file.writeResp = null;
    xref.forced = Resp.ERROR;
    assertThat(Ctx.message(post(Action.ENTER, "00000000001", "Y").response()))
        .startsWith("Payment successful.");
  }

  @Test
  void keys() {
    assertThat(post(Action.PF3, "", "").response().nextScreen()).isEqualTo(ScreenId.COMEN01);
    assertThat(post(Action.PF4, "00000000001", "").response().data().actidin()).isEmpty();
    assertThat(Ctx.message(post(Action.PF5, "", "").response()))
        .isEqualTo("Invalid key pressed. Please see below...");
    var ext = new CommareaExtension("", "", 0, false, "", "00000000001", null, Map.of());
    var first = svc.run(Ctx.fromMenu("CB00", "COBIL00C"), ext, null, Map.of());
    assertThat(Ctx.message(first.response())).isEqualTo("Confirm to make a bill payment...");
  }
}
