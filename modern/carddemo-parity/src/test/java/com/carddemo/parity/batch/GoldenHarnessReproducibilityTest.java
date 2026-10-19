package com.carddemo.parity.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.carddemo.parity.ParityPaths;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Re-runs the original COBOL under GnuCOBOL ({@code tools/golden/run-cobol.sh <JOB> --verify}) and
 * requires zero diff against the committed golden. Skipped when {@code cobc} is missing, unless
 * {@code CARDDEMO_REQUIRE_COBOL=true} (set in CI) which turns the skip into a failure.
 */
@Tag("parity")
class GoldenHarnessReproducibilityTest {

  @ParameterizedTest
  @ValueSource(strings = {"POSTTRAN", "INTCALC"})
  void rerunGivesZeroDiff(String job) throws Exception {
    boolean hasCobc = onPath("cobc") && onPath("faketime");
    if ("true".equalsIgnoreCase(System.getenv("CARDDEMO_REQUIRE_COBOL"))) {
      assertThat(hasCobc).as("cobc + faketime on PATH").isTrue();
    }
    assumeTrue(hasCobc, "GnuCOBOL not installed");
    Path root = ParityPaths.modernRoot();
    Process p =
        new ProcessBuilder("bash", "tools/golden/run-cobol.sh", job, "--verify")
            .directory(root.toFile())
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
