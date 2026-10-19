package com.carddemo.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FixedBusinessClockTest {

  @Test
  void goldenClockMatchesHarnessTimestamp() {
    assertThat(FixedBusinessClock.golden().cobolTimestamp())
        .isEqualTo("2022-07-18-12.00.00.000000");
  }
}
