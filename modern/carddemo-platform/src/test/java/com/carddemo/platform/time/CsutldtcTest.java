package com.carddemo.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CsutldtcTest {

  private final Csutldtc csutldtc = new Csutldtc(new CeeDays(FixedBusinessClock.golden()));

  @Test
  void buildsThe80ByteResultLikeTheCobol() {
    Csutldtc.Result result = csutldtc.call("2022-07-18", "YYYY-MM-DD");

    assertThat(result.message())
        .hasSize(80)
        .isEqualTo(
            "0000Mesg Code: 0000 Date is valid   TstDate: \u0000\n2022-07- Mask used:YYYY-MM-DD    ");
    assertThat(result.returnCode()).isZero();
    assertThat(result.valid()).isTrue();
  }

  @ParameterizedTest(name = "''{0}'' / ''{1}''")
  @CsvSource(
      delimiter = '|',
      value = {
        "2022-07-18|YYYY-MM-DD|0000|0000|'Date is valid  '",
        "2022-07|YYYY-MM-DD|0003|2507|'Insufficient   '",
        "2022-02-30|YYYY-MM-DD|0003|2508|'Datevalue error'",
        "1500-01-01|YYYY-MM-DD|0003|2513|'Unsupp. Range  '",
        "2022-13-01|YYYY-MM-DD|0003|2517|'Invalid month  '",
        "20220718|QQQQ|0003|2518|'Bad Pic String '",
        "2022-XX-18|YYYY-MM-DD|0003|2520|'Nonnumeric data'"
      })
  void mapsEveryFeedbackToTheSameCodesAndTexts(
      String date, String mask, String severity, String msgNo, String text) {
    Csutldtc.Result result = csutldtc.call(date, mask);

    assertThat(result.severityCode()).isEqualTo(severity);
    assertThat(result.messageCode()).isEqualTo(msgNo);
    assertThat(result.resultText()).isEqualTo(text);
    assertThat(result.returnCode()).isEqualTo(Integer.parseInt(severity));
  }

  @Test
  void resultTextsAreFifteenBytes() {
    for (CeeDays.Feedback f : CeeDays.Feedback.values()) {
      assertThat(Csutldtc.resultText(f)).hasSize(15);
    }
    assertThat(Csutldtc.resultText(CeeDays.Feedback.INVALID_ERA)).isEqualTo("Invalid Era    ");
    assertThat(Csutldtc.resultText(CeeDays.Feedback.YEAR_IN_ERA_ZERO)).isEqualTo("YearInEra is 0 ");
  }
}
