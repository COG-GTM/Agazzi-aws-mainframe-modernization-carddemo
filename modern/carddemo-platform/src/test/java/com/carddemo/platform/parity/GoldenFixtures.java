package com.carddemo.platform.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Locations of the 1c-platform golden masters and the GnuCOBOL re-run used by parity tests. */
final class GoldenFixtures {

  private GoldenFixtures() {}

  static Path modernRoot() {
    String root = System.getProperty("modern.root");
    Path path = root != null ? Path.of(root) : Path.of("..");
    return path.toAbsolutePath().normalize();
  }

  static Path golden(String job) {
    return modernRoot()
        .resolve("carddemo-parity/src/test/resources/golden/1c-platform")
        .resolve(job);
  }

  /**
   * Re-runs {@code capture.py <JOB> --verify} (original COBOL under GnuCOBOL) and requires zero
   * diff. Skipped without {@code cobc}, unless {@code CARDDEMO_REQUIRE_COBOL=true} (CI).
   */
  static void verifyWithGnuCobol(String job) throws Exception {
    boolean hasCobc = onPath("cobc") && onPath("faketime");
    if ("true".equalsIgnoreCase(System.getenv("CARDDEMO_REQUIRE_COBOL"))) {
      assertThat(hasCobc).as("cobc + faketime on PATH").isTrue();
    }
    assumeTrue(hasCobc, "GnuCOBOL not installed");
    Process p =
        new ProcessBuilder(
                "python3",
                "carddemo-parity/src/test/resources/golden/1c-platform/capture.py",
                job,
                "--verify")
            .directory(modernRoot().toFile())
            .redirectErrorStream(true)
            .start();
    String log = new String(p.getInputStream().readAllBytes());
    assertThat(p.waitFor(10, TimeUnit.MINUTES)).isTrue();
    assertThat(p.exitValue()).as(log).isZero();
  }

  private static boolean onPath(String exe) {
    for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
      if (Files.isExecutable(Path.of(dir, exe))) {
        return true;
      }
    }
    return false;
  }
}
