package com.carddemo.contracts.port;

import java.time.Duration;

/** Timed wait (MVSWAIT/COBSWAIT: PARM in hundredths of a second). Implemented by 1C. */
public interface WaitService {

  void await(Duration duration) throws InterruptedException;

  /** Same unit as MVSWAIT STIMER BINTVL (1/100 s). */
  default void awaitCentiseconds(long centiseconds) throws InterruptedException {
    await(Duration.ofMillis(centiseconds * 10));
  }
}
