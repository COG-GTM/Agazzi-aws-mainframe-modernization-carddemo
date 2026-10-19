package com.carddemo.ext.authorization;

import com.carddemo.contracts.port.BusinessClock;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Test clock that scenarios can move (FAKETIME equivalent). */
public class MutableBusinessClock implements BusinessClock {
  private volatile Clock clock;

  public MutableBusinessClock(LocalDateTime at) {
    set(at);
  }

  public final void set(LocalDateTime at) {
    clock = Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  }

  @Override
  public Clock clock() {
    return clock;
  }
}
