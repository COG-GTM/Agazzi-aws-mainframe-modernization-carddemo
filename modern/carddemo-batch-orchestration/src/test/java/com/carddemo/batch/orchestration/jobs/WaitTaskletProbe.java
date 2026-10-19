package com.carddemo.batch.orchestration.jobs;

/** Test access to the package-private COBSWAIT SYSIN conversion. */
public final class WaitTaskletProbe {
  private WaitTaskletProbe() {}

  public static long centiseconds(String sysin) {
    return WaitTasklet.centiseconds(sysin);
  }
}
