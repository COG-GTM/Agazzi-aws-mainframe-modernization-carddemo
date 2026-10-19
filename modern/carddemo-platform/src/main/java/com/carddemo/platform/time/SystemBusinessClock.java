package com.carddemo.platform.time;

import com.carddemo.contracts.port.BusinessClock;
import java.time.Clock;

/** Fase 0 production clock (system zone). 1C replaces it with ASKTIME/FORMATTIME semantics. */
public final class SystemBusinessClock implements BusinessClock {

  private final Clock clock;

  public SystemBusinessClock(Clock clock) {
    this.clock = clock;
  }

  public SystemBusinessClock() {
    this(Clock.systemDefaultZone());
  }

  @Override
  public Clock clock() {
    return clock;
  }
}
