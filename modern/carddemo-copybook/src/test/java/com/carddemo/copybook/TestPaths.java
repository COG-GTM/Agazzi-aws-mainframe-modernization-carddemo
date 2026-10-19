package com.carddemo.copybook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Locations of the legacy sources/datasets relative to the {@code modern/} root. */
public final class TestPaths {

  private TestPaths() {}

  public static Path modern() {
    return Path.of(System.getProperty("modern.root", "..")).toAbsolutePath().normalize();
  }

  public static Path app() {
    return modern().resolve("../app").normalize();
  }

  public static Path goldenRoot() {
    return modern().resolve("carddemo-parity/src/test/resources/golden");
  }

  public static byte[] bytes(Path p) {
    try {
      return Files.readAllBytes(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
