package com.carddemo.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CicsTimeTest {

  private final FixedBusinessClock clock = FixedBusinessClock.golden();

  @Test
  void askTimeIsMillisecondsSince1900() {
    assertThat(CicsTime.toAbsTime(LocalDateTime.of(1900, 1, 1, 0, 0))).isZero();
    assertThat(CicsTime.toAbsTime(LocalDateTime.of(1900, 1, 2, 0, 0))).isEqualTo(86_400_000L);
    long abs = CicsTime.askTime(clock);
    assertThat(abs).isEqualTo(3_867_134_400_000L);
    assertThat(CicsTime.fromAbsTime(abs)).isEqualTo(FixedBusinessClock.GOLDEN_TIMESTAMP);
  }

  @Test
  void formatTimeFields() {
    long abs = CicsTime.toAbsTime(LocalDateTime.of(2024, 2, 29, 7, 5, 9, 990_000_000));

    assertThat(CicsTime.yyyymmdd(abs, '-')).isEqualTo("2024-02-29");
    assertThat(CicsTime.yyyymmdd(abs, null)).isEqualTo("20240229");
    assertThat(CicsTime.mmddyy(abs, '/')).isEqualTo("02/29/24");
    assertThat(CicsTime.mmddyy(abs, null)).isEqualTo("022924");
    assertThat(CicsTime.time(abs, ':')).isEqualTo("07:05:09");
    assertThat(CicsTime.time(abs, null)).isEqualTo("070509");
    assertThat(CicsTime.timestamp(abs)).hasSize(26).isEqualTo("2024-02-29 07:05:09.000000");
  }

  @Test
  void currentDateLikeCsdat01y() {
    assertThat(CicsTime.currentDate(clock)).isEqualTo("2022071812000000");
    FixedBusinessClock hundredths =
        new FixedBusinessClock(LocalDateTime.of(1999, 12, 31, 23, 59, 59, 999_999_999));
    assertThat(CicsTime.currentDate(hundredths)).isEqualTo("1999123123595999");
  }
}
