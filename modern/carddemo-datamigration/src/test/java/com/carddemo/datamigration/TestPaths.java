package com.carddemo.datamigration;

import java.nio.file.Path;

/** Repository paths for tests ({@code modern.root} is set by surefire/failsafe). */
public final class TestPaths {
  private TestPaths() {}

  public static Path modernRoot() {
    return Path.of(System.getProperty("modern.root", "..")).toAbsolutePath().normalize();
  }

  public static Path appData(String format) {
    return modernRoot().resolve("../app/data").resolve(format).normalize();
  }

  public static Path listcat() {
    return modernRoot().resolve("../app/catlg/LISTCAT.txt").normalize();
  }

  public static Path golden() {
    return modernRoot().resolve("carddemo-parity/src/test/resources/golden/1b-datamigration");
  }
}
