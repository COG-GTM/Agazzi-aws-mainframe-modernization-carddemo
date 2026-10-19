package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.platform.time.FixedBusinessClock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SystemDateInquiryTest {

  @Test
  void formatsBusinessClockLikeFormattime() {
    InquiryOutcome out =
        new SystemDateInquiry(new FixedBusinessClock(LocalDateTime.of(2024, 2, 9, 7, 5, 3)))
            .process("ignored");
    assertThat(out.reply()).isTrue();
    assertThat(out.payload())
        .hasSize(1000)
        .startsWith("SYSTEM DATE : 02-09-2024SYSTEM TIME : 07:05:03 ")
        .endsWith("   ");
  }
}
