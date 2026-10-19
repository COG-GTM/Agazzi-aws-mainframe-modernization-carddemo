package com.carddemo.online.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.platform.time.FixedBusinessClock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Cotrn01cServiceTest {

  private final Fakes.Transact file = new Fakes.Transact().with(3);
  private final Cotrn01cService svc = new Cotrn01cService(file, FixedBusinessClock.golden());

  @Test
  void firstSendFromMenuIsEmpty() {
    var r = svc.run(Ctx.fromMenu("CT01", "COTRN01C"), CommareaExtension.initial(), null, Map.of());
    assertThat(r.response().messages()).isEmpty();
    assertThat(r.response().data().trnid()).isNull();
  }

  @Test
  void firstSendWithSelectionShowsDetail() {
    var ext = new CommareaExtension("", "", 1, false, "S", Fakes.id(2), "ENTER", Map.of());
    var r = svc.run(Ctx.fromMenu("CT01", "COTRN01C"), ext, null, Map.of());
    var d = r.response().data();
    assertThat(d.trnid()).isEqualTo(Fakes.id(2));
    assertThat(d.trnamt()).isEqualTo("+00000002.50");
    assertThat(d.tcatcd()).isEqualTo("0005");
    assertThat(d.mid()).isEqualTo("000000123");
    assertThat(d.torigdt()).isEqualTo("2022-06-01");
    assertThat(r.extension().xctlAid()).isNull();
  }

  @Test
  void enterKeyBranches() {
    var p = svc.run(Ctx.fromMenu("CT01", "COTRN01C"), CommareaExtension.initial(), null, Map.of());
    var ctx = p.response().context();
    var empty = svc.run(ctx, p.extension(), Action.ENTER, Map.of("TRNIDIN", ""));
    assertThat(Ctx.message(empty.response())).isEqualTo("Tran ID can NOT be empty...");
    assertThat(empty.response().fieldErrors()).extracting("field").containsExactly("TRNIDIN");
    var nf = svc.run(ctx, p.extension(), Action.ENTER, Map.of("TRNIDIN", Fakes.id(9)));
    assertThat(Ctx.message(nf.response())).isEqualTo("Transaction ID NOT found...");
    var ok = svc.run(ctx, p.extension(), Action.ENTER, Map.of("TRNIDIN", Fakes.id(1)));
    assertThat(ok.response().data().mname()).isEqualTo("SHOP");
    var clr = svc.run(ctx, p.extension(), Action.PF4, Map.of("TRNIDIN", Fakes.id(1)));
    assertThat(clr.response().data().trnidin()).isEmpty();
    var bad = svc.run(ctx, p.extension(), Action.PF7, Map.of());
    assertThat(Ctx.message(bad.response())).isEqualTo("Invalid key pressed. Please see below...");
  }

  @Test
  void navigation() {
    var p = svc.run(Ctx.fromMenu("CT01", "COTRN01C"), CommareaExtension.initial(), null, Map.of());
    var pf3 = svc.run(p.response().context(), p.extension(), Action.PF3, Map.of());
    assertThat(pf3.response().nextScreen()).isEqualTo(ScreenId.COMEN01);
    var pf5 = svc.run(p.response().context(), p.extension(), Action.PF5, Map.of());
    assertThat(pf5.response().nextScreen()).isEqualTo(ScreenId.COTRN00);
    assertThat(pf5.extension().xctlAid()).isEqualTo("PF5");
    assertThat(pf5.response().context().fromProgram()).isEqualTo("COTRN01C");
  }
}
