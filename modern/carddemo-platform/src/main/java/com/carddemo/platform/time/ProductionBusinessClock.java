package com.carddemo.platform.time;

import com.carddemo.contracts.port.BusinessClock;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Production {@link BusinessClock}: the system clock in the business time zone (the CICS region
 * local time used by {@code ASKTIME}), ticking in microseconds so that {@link #cobolTimestamp()}
 * ({@code YYYY-MM-DD-HH.MM.SS.FFFFFF}, DB2 {@code CURRENT TIMESTAMP}) never carries truncated
 * nanoseconds and {@code now()} round-trips through the 26-byte timestamp.
 */
public final class ProductionBusinessClock implements BusinessClock {

  private static final Duration MICROSECOND = Duration.ofNanos(1_000);

  private final Clock clock;

  public ProductionBusinessClock(ZoneId businessZone) {
    this(Clock.system(Objects.requireNonNull(businessZone, "businessZone")));
  }

  /** Wraps any clock (offset or fixed for tests), adding microsecond ticking. */
  public ProductionBusinessClock(Clock base) {
    this.clock = Clock.tick(Objects.requireNonNull(base, "base"), MICROSECOND);
  }

  @Override
  public Clock clock() {
    return clock;
  }

  public ZoneId zone() {
    return clock.getZone();
  }
}
