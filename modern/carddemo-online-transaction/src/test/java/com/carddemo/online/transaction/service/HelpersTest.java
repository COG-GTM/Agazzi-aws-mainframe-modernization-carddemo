package com.carddemo.online.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Corpt00Screen;
import com.carddemo.contracts.screen.map.Cotrn01Screen;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HelpersTest {

  @Test
  void cobolFormats() {
    assertThat(CobolFmt.editSigned(new BigDecimal("-12.345"), 8)).isEqualTo("-00000012.34");
    assertThat(CobolFmt.editSigned(new BigDecimal("1234567890.10"), 8)).isEqualTo("+34567890.10");
    assertThat(CobolFmt.editSigned(null, 10)).isEqualTo("+0000000000.00");
    assertThat(CobolFmt.numvalC(" -1,234.50 ")).isEqualByComparingTo("-1234.50");
    assertThat(CobolFmt.numvalC("$12.5-")).isEqualByComparingTo("-12.5");
    assertThat(CobolFmt.numvalC("7CR")).isEqualByComparingTo("-7");
    assertThat(CobolFmt.numvalC(".5")).isEqualByComparingTo("0.5");
    assertThat(CobolFmt.numvalC("5.")).isEqualByComparingTo("5");
    assertThat(CobolFmt.numvalC("abc")).isEqualByComparingTo("0");
    assertThat(CobolFmt.numvalC(null)).isEqualByComparingTo("0");
    assertThat(CobolFmt.numvalToDigits("123", 2)).isEqualTo("23");
    assertThat(CobolFmt.zonedMove("1 ")).isEqualTo(10L);
    assertThat(CobolFmt.zonedMove("")).isNull();
    assertThat(CobolFmt.zonedMove("1A")).isNull();
    assertThat(CobolFmt.digits(123, 2)).isEqualTo("23");
    assertThat(CobolFmt.isNumeric("")).isFalse();
    assertThat(CobolFmt.isNumeric(null)).isFalse();
    assertThat(CobolFmt.blank("\0 ")).isTrue();
    assertThat(CobolFmt.rtrim(null)).isNull();
    assertThat(CobolFmt.pad("abc", 2)).isEqualTo("ab");
  }

  @Test
  void ceeDays() {
    assertThat(CeeDays.accepted("2022-02-28")).isTrue();
    assertThat(CeeDays.accepted("2024-02-29")).isTrue();
    assertThat(CeeDays.accepted("2023-02-29")).isFalse();
    assertThat(CeeDays.accepted("2022-00-10")).isFalse();
    assertThat(CeeDays.feedback("1500-01-01")).isEqualTo(2513);
    assertThat(CeeDays.accepted("1500-01-01")).isTrue();
    assertThat(CeeDays.accepted("2022/01/01")).isFalse();
    assertThat(CeeDays.accepted(null)).isFalse();
  }

  @Test
  void bmsMaps() {
    var in = new Corpt00Screen.Input("X", null, "", "1", null, null, null, null, null, "y");
    Map<String, String> m = BmsMaps.receive(Corpt00Screen.Input.class, in, Set.of("SDTMM"));
    assertThat(m.get("MONTHLY")).isEqualTo("X");
    assertThat(m.get("SDTMM")).isEqualTo("01");
    assertThat(m.get("YEARLY")).isEmpty();
    assertThat(BmsMaps.receive(Cotrn01Screen.Input.class, null, Set.of()).get("TRNIDIN")).isEmpty();
    assertThat(BmsMaps.lengths(Cotrn01Screen.Output.class)).containsEntry("TDESC", 60);
  }

  @Test
  void navigationAndBuffer() {
    assertThat(Navigation.screenOf("COADM01C")).isEqualTo(ScreenId.COADM01);
    assertThat(Navigation.screenOf(null)).isEqualTo(ScreenId.COSGN00);
    var ctx = Ctx.fromMenu("CT00", "COTRN00C");
    assertThat(Navigation.xctl(ctx, "CT00", "COTRN00C", " ").toProgram()).isEqualTo("COSGN00C");
    assertThat(Navigation.enter(ctx.reenter()).programContext())
        .isEqualTo(com.carddemo.contracts.screen.ConversationContext.ProgramContext.ENTER);
    var buf =
        new MapBuffer(
            ScreenId.COTRN01,
            Cotrn01Screen.Output.class,
            com.carddemo.platform.time.FixedBusinessClock.golden(),
            false);
    assertThat(buf.sent()).isFalse();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> buf.set("NOPE", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
