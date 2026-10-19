package com.carddemo.online.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Cotrn00Screen;
import com.carddemo.platform.time.FixedBusinessClock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Cotrn00cServiceTest {

  private final Fakes.Transact file = new Fakes.Transact();
  private final Cotrn00cService svc = new Cotrn00cService(file, FixedBusinessClock.golden());

  private ProgramResult<Cotrn00Screen.Output> first() {
    return svc.run(Ctx.fromMenu("CT00", "COTRN00C"), CommareaExtension.initial(), null, Map.of());
  }

  private ProgramResult<Cotrn00Screen.Output> next(
      ProgramResult<Cotrn00Screen.Output> prev, Action aid, Map<String, String> fields) {
    return svc.run(prev.response().context(), prev.extension(), aid, new HashMap<>(fields));
  }

  @Test
  void emptyFileShowsTopOfPage() {
    var r = first().response();
    assertThat(Ctx.message(r)).isEqualTo("You are at the top of the page...");
    assertThat(r.data().pagenum()).isEqualTo("00000000");
    assertThat(r.data().trnname()).isEqualTo("CT00");
    assertThat(r.data().curdate()).isEqualTo("07/18/22");
    assertThat(r.context().programContext()).isEqualTo(ConversationContext.ProgramContext.REENTER);
  }

  @Test
  void firstPageAndPaging() {
    file.with(25);
    var p1 = first();
    var d = p1.response().data();
    assertThat(d.trnid01()).isEqualTo(Fakes.id(1));
    assertThat(d.trnid10()).isEqualTo(Fakes.id(10));
    assertThat(d.tamt001()).isEqualTo("+00000001.50");
    assertThat(d.tdate01()).isEqualTo("06/01/22");
    assertThat(d.tdesc01()).isEqualTo("DESC 1");
    assertThat(d.pagenum()).isEqualTo("00000001");
    assertThat(p1.response().messages()).isEmpty();
    assertThat(p1.extension().nextPage()).isTrue();

    var p2 = next(p1, Action.PF8, Map.of());
    assertThat(p2.response().data().trnid01()).isEqualTo(Fakes.id(11));
    assertThat(p2.response().data().pagenum()).isEqualTo("00000002");

    var p3 = next(p2, Action.PF8, Map.of());
    assertThat(p3.response().data().trnid01()).isEqualTo(Fakes.id(21));
    assertThat(p3.response().data().trnid05()).isEqualTo(Fakes.id(25));
    assertThat(p3.response().data().trnid06()).isEmpty();
    assertThat(Ctx.message(p3.response())).isEqualTo("You have reached the bottom of the page...");
    assertThat(p3.extension().nextPage()).isFalse();

    var bottom = next(p3, Action.PF8, Map.of());
    assertThat(Ctx.message(bottom.response()))
        .isEqualTo("You are already at the bottom of the page...");
    assertThat(bottom.response().data().trnid01()).isEqualTo(Fakes.id(21));

    var back = next(bottom, Action.PF7, Map.of());
    assertThat(back.response().data().trnid01()).isEqualTo(Fakes.id(11));
    assertThat(back.response().data().pagenum()).isEqualTo("00000002");

    var back1 = next(back, Action.PF7, Map.of());
    assertThat(back1.response().data().trnid01()).isEqualTo(Fakes.id(1));
    assertThat(back1.response().data().pagenum()).isEqualTo("00000001");
    assertThat(Ctx.message(back1.response())).isEqualTo("You have reached the top of the page...");

    var top = next(back1, Action.PF7, Map.of());
    assertThat(Ctx.message(top.response())).isEqualTo("You are already at the top of the page...");
  }

  @Test
  void exactlyTenRowsReachesBottomOnLookahead() {
    file.with(10);
    var r = first();
    assertThat(Ctx.message(r.response())).isEqualTo("You have reached the bottom of the page...");
    assertThat(r.response().data().trnid10()).isEqualTo(Fakes.id(10));
    assertThat(r.extension().nextPage()).isFalse();
  }

  @Test
  void startFromTypedTranId() {
    file.with(30);
    var r = next(first(), Action.ENTER, Map.of("TRNIDIN", Fakes.id(15)));
    assertThat(r.response().data().trnid01()).isEqualTo(Fakes.id(15));
    assertThat(r.response().data().trnidin()).isEmpty();
  }

  @Test
  void nonNumericTranId() {
    file.with(3);
    var r = next(first(), Action.ENTER, Map.of("TRNIDIN", CobolFmt.pad("12", 16)));
    assertThat(Ctx.message(r.response())).isEqualTo("Tran ID must be Numeric ...");
    assertThat(r.response().fieldErrors()).extracting("field").containsExactly("TRNIDIN");
  }

  @Test
  void keyBeyondLastRecord() {
    file.with(3);
    var r = next(first(), Action.ENTER, Map.of("TRNIDIN", Fakes.id(99)));
    assertThat(Ctx.message(r.response())).isEqualTo("You are at the top of the page...");
  }

  @Test
  void selectionTransfersToCt01() {
    file.with(12);
    var r = next(first(), Action.ENTER, Map.of("SEL0003", "s"));
    assertThat(r.response().nextScreen()).isEqualTo(ScreenId.COTRN01);
    assertThat(r.response().context().fromProgram()).isEqualTo("COTRN00C");
    assertThat(r.response().context().toProgram()).isEqualTo("COTRN01C");
    assertThat(r.extension().selected()).isEqualTo(Fakes.id(3));
    assertThat(r.extension().xctlAid()).isEqualTo("ENTER");
  }

  @Test
  void invalidSelectionStillLists() {
    file.with(12);
    var r = next(first(), Action.ENTER, Map.of("SEL0001", "X"));
    assertThat(Ctx.message(r.response())).isEqualTo("Invalid selection. Valid value is S");
    assertThat(r.response().fieldErrors()).isEmpty();
    assertThat(r.response().data().trnid01()).isEqualTo(Fakes.id(1));
  }

  @Test
  void pf3BackToMenuAndInvalidKey() {
    var p = first();
    var r = next(p, Action.PF3, Map.of());
    assertThat(r.response().nextScreen()).isEqualTo(ScreenId.COMEN01);
    var k = next(p, Action.PF5, Map.of());
    assertThat(Ctx.message(k.response())).isEqualTo("Invalid key pressed. Please see below...");
  }

  @Test
  void returningWithPf5SkipsFirstRecordLikeCobol() {
    file.with(12);
    var ext = new CommareaExtension("", "", 0, false, "S", Fakes.id(1), "PF5", Map.of());
    var ctx = Ctx.fromMenu("CT01", "COTRN01C").transferTo("CT01", "COTRN01C", "CT00", "COTRN00C");
    var r = svc.run(ctx, ext, null, Map.of());
    assertThat(r.response().data().trnid01()).isEqualTo(Fakes.id(2));
  }
}
