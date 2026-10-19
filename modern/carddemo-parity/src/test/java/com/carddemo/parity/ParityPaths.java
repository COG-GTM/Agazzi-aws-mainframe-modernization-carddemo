package com.carddemo.parity;

import java.nio.file.Path;

/** Paths used by parity tests ({@code modern.root} is set by surefire/failsafe). */
public final class ParityPaths {
  private ParityPaths() {}

  public static Path modernRoot() {
    return Path.of(System.getProperty("modern.root", "..")).toAbsolutePath().normalize();
  }

  public static Path onlineGoldens() {
    return modernRoot().resolve("carddemo-parity/src/test/resources/golden/online");
  }
}
