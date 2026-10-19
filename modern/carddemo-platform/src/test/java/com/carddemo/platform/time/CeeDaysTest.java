package com.carddemo.platform.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.platform.time.CeeDays.Feedback;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CeeDaysTest {

  private final CeeDays ceeDays = new CeeDays(FixedBusinessClock.golden());

  @ParameterizedTest(name = "''{0}'' / ''{1}'' -> {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        // Lillian anchors: day 1 and the IBM documented example
        "1582-10-15|YYYY-MM-DD|VALID|1",
        "1988-05-16|YYYY-MM-DD|VALID|148138",
        "2000-01-01|YYYY-MM-DD|VALID|152385",
        "20220718|YYYYMMDD|VALID|160619",
        "9999-12-31|YYYY-MM-DD|VALID|3074324",
        // leap years: /4, /100 not, /400 yes
        "2024-02-29|YYYY-MM-DD|VALID|161210",
        "2000-02-29|YYYY-MM-DD|VALID|152444",
        "1900-02-29|YYYY-MM-DD|BAD_DATE_VALUE|0",
        "2023-02-29|YYYY-MM-DD|BAD_DATE_VALUE|0",
        "2100-02-29|YYYY-MM-DD|BAD_DATE_VALUE|0",
        // month / day limits
        "2022-00-10|YYYY-MM-DD|INVALID_MONTH|0",
        "2022-13-01|YYYY-MM-DD|INVALID_MONTH|0",
        "2022-04-31|YYYY-MM-DD|BAD_DATE_VALUE|0",
        "2022-12-32|YYYY-MM-DD|BAD_DATE_VALUE|0",
        "2022-01-00|YYYY-MM-DD|BAD_DATE_VALUE|0",
        // Gregorian range
        "1582-10-14|YYYY-MM-DD|UNSUPPORTED_RANGE|0",
        "1500-01-01|YYYY-MM-DD|UNSUPPORTED_RANGE|0",
        "0000-01-01|YYYY-MM-DD|UNSUPPORTED_RANGE|0",
        // non numeric / separators
        "2022-0A-18|YYYY-MM-DD|NON_NUMERIC_DATA|0",
        "2022/07/18|YYYY-MM-DD|NON_NUMERIC_DATA|0",
        "' 2022-7-18'|YYYY-MM-DD|NON_NUMERIC_DATA|0",
        // insufficient data
        "2022-07|YYYY-MM-DD|INSUFFICIENT_DATA|0",
        "''|YYYYMMDD|INSUFFICIENT_DATA|0",
        // bad picture strings
        "20220718|''|BAD_PIC_STRING|0",
        "20220718|YYYYMM|BAD_PIC_STRING|0",
        "20220718|XXXXXXXX|BAD_PIC_STRING|0",
        "2022071818|YYYYMMDDDD|BAD_PIC_STRING|0",
        "2022199|YYYYMMDDD|BAD_PIC_STRING|0",
        // Julian and two-digit years (window 1942-2041 from the 2022 business clock)
        "2022199|YYYYDDD|VALID|160619",
        "2024366|YYYYDDD|VALID|161516",
        "2023366|YYYYDDD|BAD_DATE_VALUE|0",
        "220718|YYMMDD|VALID|160619",
        "07/18/42|MM/DD/YY|VALID|131399",
        "07/18/41|MM/DD/YY|VALID|167559"
      })
  void convertsLikeCeedays(String date, String picture, Feedback feedback, int lillian) {
    CeeDays.Outcome outcome = ceeDays.toLillian(date, picture);

    assertThat(outcome.feedback()).isEqualTo(feedback);
    assertThat(outcome.lillian()).isEqualTo(lillian);
    if (outcome.valid()) {
      assertThat(CeeDays.fromLillian(lillian).getYear()).isBetween(1582, 9999);
    }
  }

  @Test
  void ignoresTrailingSpacesAndLowValuesOfTheVstrings() {
    assertThat(ceeDays.toLillian("20220718\0\0", "YYYYMMDD  ").lillian()).isEqualTo(160619);
    assertThat(ceeDays.toLillian("2022-07-18", "YYYY-MM-DD").lillian()).isEqualTo(160619);
  }

  @Test
  void lillianRoundTrips() {
    assertThat(CeeDays.fromLillian(1)).isEqualTo(LocalDate.of(1582, 10, 15));
    assertThat(CeeDays.fromLillian(148138)).isEqualTo(LocalDate.of(1988, 5, 16));
    assertThatThrownBy(() -> CeeDays.fromLillian(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "VALID,0000000000000000",
    "INSUFFICIENT_DATA,000309CB59C3C5C5",
    "BAD_DATE_VALUE,000309CC59C3C5C5",
    "INVALID_ERA,000309CD59C3C5C5",
    "UNSUPPORTED_RANGE,000309D159C3C5C5",
    "INVALID_MONTH,000309D559C3C5C5",
    "BAD_PIC_STRING,000309D659C3C5C5",
    "NON_NUMERIC_DATA,000309D859C3C5C5",
    "YEAR_IN_ERA_ZERO,000309D959C3C5C5"
  })
  void feedbackTokensMatchTheCsutldtcLevel88s(Feedback feedback, String token) {
    assertThat(feedback.tokenHex()).isEqualTo(token);
  }

  @ParameterizedTest
  @CsvSource({"1600,true", "2000,true", "2024,true", "1700,false", "1900,false", "2023,false"})
  void gregorianLeapYears(int year, boolean leap) {
    assertThat(CeeDays.isLeap(year)).isEqualTo(leap);
  }
}
