package com.carddemo.online.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.port.ReportJobLauncherPort;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportJobHandle;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportRequest;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportType;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Corpt00Screen;
import com.carddemo.platform.time.FixedBusinessClock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Corpt00cServiceTest {

  private final ReportJobLauncherPort launcher = mock(ReportJobLauncherPort.class);
  private final Corpt00cService svc = new Corpt00cService(launcher, FixedBusinessClock.golden());

  private ProgramResult<Corpt00Screen.Output> post(Action aid, Map<String, String> f) {
    var p = svc.run(Ctx.fromMenu("CR00", "CORPT00C"), CommareaExtension.initial(), null, Map.of());
    var in = new HashMap<String, String>(f);
    for (String k : Corpt00cService.NUM_FIELDS) {
      String v = in.get(k);
      if (v != null && !v.isBlank()) {
        int len = k.endsWith("YYYY") ? 4 : 2;
        in.put(k, "0".repeat(Math.max(0, len - v.length())) + v);
      }
    }
    return svc.run(p.response().context(), p.extension(), aid, in);
  }

  @Test
  void monthlyReport() {
    when(launcher.launch(any())).thenReturn(new ReportJobHandle(42, "TRANREPT", "STARTED"));
    var r = post(Action.ENTER, Map.of("MONTHLY", "X", "CONFIRM", "Y"));
    assertThat(Ctx.message(r.response())).isEqualTo("Monthly report submitted for printing ...");
    assertThat(Ctx.green(r.response())).isTrue();
    assertThat(r.reportExecutionId()).isEqualTo(42L);
    verify(launcher)
        .launch(
            new ReportRequest(
                ReportType.MONTHLY,
                LocalDate.of(2022, 7, 1),
                LocalDate.of(2022, 7, 31),
                "USER0001"));
  }

  @Test
  void yearlyAndCustom() {
    when(launcher.launch(any())).thenReturn(new ReportJobHandle(7, "TRANREPT", "STARTED"));
    assertThat(Ctx.message(post(Action.ENTER, Map.of("YEARLY", "X", "CONFIRM", "y")).response()))
        .isEqualTo("Yearly report submitted for printing ...");
    var c =
        post(
            Action.ENTER,
            Map.of(
                "CUSTOM", "X", "SDTMM", "1", "SDTDD", "2", "SDTYYYY", "2022", "EDTMM", "3", "EDTDD",
                "4", "EDTYYYY", "2022", "CONFIRM", "Y"));
    assertThat(Ctx.message(c.response())).isEqualTo("Custom report submitted for printing ...");
    verify(launcher)
        .launch(
            new ReportRequest(
                ReportType.CUSTOM, LocalDate.of(2022, 1, 2), LocalDate.of(2022, 3, 4), "USER0001"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "SDTMM||Start Date - Month can NOT be empty...|SDTMM",
        "SDTDD||Start Date - Day can NOT be empty...|SDTDD",
        "SDTYYYY||Start Date - Year can NOT be empty...|SDTYYYY",
        "EDTMM||End Date - Month can NOT be empty...|EDTMM",
        "EDTDD||End Date - Day can NOT be empty...|EDTDD",
        "EDTYYYY||End Date - Year can NOT be empty...|EDTYYYY",
        "SDTMM|13|Start Date - Not a valid Month...|SDTMM",
        "SDTDD|32|Start Date - Not a valid Day...|SDTDD",
        "EDTMM|13|End Date - Not a valid Month...|EDTMM",
        "EDTDD|32|End Date - Not a valid Day...|EDTDD",
        "SDTDD|31|Start Date - Not a valid date...|SDTMM",
        "EDTDD|30|End Date - Not a valid date...|EDTMM",
        "CONFIRM||Please confirm to print the Custom report...|CONFIRM",
        "CONFIRM|Q|\"Q\" is not a valid value to confirm...|CONFIRM"
      })
  void customValidations(String field, String value, String message, String cursor) {
    when(launcher.launch(any())).thenReturn(new ReportJobHandle(1, "TRANREPT", "STARTED"));
    Map<String, String> f = new HashMap<>();
    f.putAll(
        Map.of(
            "CUSTOM", "X", "SDTMM", "02", "SDTDD", "01", "SDTYYYY", "2022", "EDTMM", "02", "EDTDD",
            "28", "EDTYYYY", "2022", "CONFIRM", "Y"));
    f.put(field, value == null ? "" : value);
    var r = post(Action.ENTER, f);
    assertThat(Ctx.message(r.response())).isEqualTo(message);
    assertThat(r.response().fieldErrors()).extracting("field").containsExactly(cursor);
  }

  @Test
  void otherPaths() {
    assertThat(Ctx.message(post(Action.ENTER, Map.of()).response()))
        .isEqualTo("Select a report type to print report...");
    var n = post(Action.ENTER, Map.of("MONTHLY", "X", "CONFIRM", "N"));
    assertThat(n.response().messages()).isEmpty();
    when(launcher.launch(any())).thenThrow(new UnsupportedOperationException("B3"));
    assertThat(Ctx.message(post(Action.ENTER, Map.of("MONTHLY", "X", "CONFIRM", "Y")).response()))
        .isEqualTo("Unable to Write TDQ (JOBS)...");
    assertThat(post(Action.PF3, Map.of()).response().nextScreen()).isEqualTo(ScreenId.COMEN01);
    assertThat(Ctx.message(post(Action.PF4, Map.of()).response()))
        .isEqualTo("Invalid key pressed. Please see below...");
  }
}
