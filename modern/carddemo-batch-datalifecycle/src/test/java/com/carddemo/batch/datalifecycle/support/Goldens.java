package com.carddemo.batch.datalifecycle.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/** Locates seed data and COBOL golden masters from the module tests. */
public final class Goldens {

  private Goldens() {}

  public static Path modernRoot() {
    String root = System.getProperty("modern.root");
    Path p = root != null ? Path.of(root) : Path.of("..").toAbsolutePath().normalize();
    return Files.isDirectory(p.resolve("carddemo-parity")) ? p : p.getParent();
  }

  public static Path seed(String file) {
    return modernRoot().resolve("../app/data/ASCII").resolve(file).normalize();
  }

  private static Path goldenRoot() {
    return modernRoot().resolve("carddemo-parity/src/test/resources/golden");
  }

  /** {@code golden/b4-datalifecycle/<job>/expected/<file>}. */
  public static Path b4(String job, String file) {
    return goldenRoot().resolve("b4-datalifecycle").resolve(job).resolve("expected").resolve(file);
  }

  /** {@code golden/b4-datalifecycle/<relative>}. */
  public static Path b4Path(String relative) {
    return goldenRoot().resolve("b4-datalifecycle").resolve(relative);
  }

  /** {@code golden/batch/<job>/expected/<file>} (Fase 0 goldens owned by b4). */
  public static Path batch(String job, String file) {
    return goldenRoot().resolve("batch").resolve(job).resolve("expected").resolve(file);
  }

  public static byte[] bytes(Path p) {
    try {
      return Files.readAllBytes(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Golden SYSOUT without GnuCOBOL runtime noise: the "implicit CLOSE" warnings libcob prints for
   * files the legacy program never closes are not program output.
   */
  public static String sysout(Path p) {
    String text = new String(bytes(p), StandardCharsets.ISO_8859_1);
    return text.lines()
            .filter(l -> !l.startsWith("libcob: warning: implicit CLOSE"))
            .collect(Collectors.joining("\n"))
        + "\n";
  }
}
