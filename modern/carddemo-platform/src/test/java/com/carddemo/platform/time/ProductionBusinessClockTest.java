package com.carddemo.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.BusinessClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ProductionBusinessClockTest {

  @Test
  void truncatesToMicrosecondsAndUsesTheBusinessZone() {
    Clock base = Clock.fixed(Instant.parse("2022-07-18T22:30:00.123456789Z"), ZoneId.of("UTC"));
    ProductionBusinessClock clock =
        new ProductionBusinessClock(base.withZone(ZoneId.of("America/Chicago")));

    assertThat(clock.zone()).isEqualTo(ZoneId.of("America/Chicago"));
    assertThat(clock.now()).isEqualTo(LocalDateTime.of(2022, 7, 18, 17, 30, 0, 123_456_000));
    assertThat(clock.today()).isEqualTo(LocalDate.of(2022, 7, 18));
    assertThat(clock.cobolTimestamp()).isEqualTo("2022-07-18-17.30.00.123456");
    assertThat(LocalDateTime.parse(clock.cobolTimestamp(), BusinessClock.COBOL_TIMESTAMP))
        .isEqualTo(clock.now());
  }

  @Test
  void systemClockTicks() {
    ProductionBusinessClock clock = new ProductionBusinessClock(ZoneId.of("UTC"));
    assertThat(clock.now().getNano() % 1000).isZero();
    assertThat(clock.cobolTimestamp()).hasSize(26);
  }
}
