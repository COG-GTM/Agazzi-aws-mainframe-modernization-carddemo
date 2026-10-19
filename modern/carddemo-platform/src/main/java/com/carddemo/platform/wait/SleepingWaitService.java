package com.carddemo.platform.wait;

import com.carddemo.contracts.port.WaitService;
import java.time.Duration;

/** Fase 0 MVSWAIT equivalent: plain sleep (1C moves it to virtual threads / non-blocking steps). */
public final class SleepingWaitService implements WaitService {

  @Override
  public void await(Duration duration) throws InterruptedException {
    Thread.sleep(duration);
  }
}
