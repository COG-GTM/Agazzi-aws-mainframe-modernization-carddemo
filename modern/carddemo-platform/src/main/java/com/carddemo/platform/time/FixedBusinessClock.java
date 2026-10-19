package com.carddemo.platform.time;

import com.carddemo.contracts.port.BusinessClock;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Fixed business clock for tests and golden runs (same instant as the GnuCOBOL harness). */
public final class FixedBusinessClock implements BusinessClock {

  /** Business timestamp used by the batch goldens (POSTTRAN/INTCALC job.yaml). */
  public static final LocalDateTime GOLDEN_TIMESTAMP = LocalDateTime.of(2022, 7, 18, 12, 0, 0);

  private final Clock clock;

  public FixedBusinessClock(LocalDateTime at) {
    this.clock = Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  }

  public static FixedBusinessClock golden() {
    return new FixedBusinessClock(GOLDEN_TIMESTAMP);
  }

  @Override
  public Clock clock() {
    return clock;
  }
}
