package com.carddemo.batch.posting.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The b1-posting golden masters are reproducible from the original COBOL (zero diff). Skipped
 * without GnuCOBOL unless {@code CARDDEMO_REQUIRE_COBOL=true} (CI).
 */
@Tag("parity")
class B1GoldenReproducibilityTest {

  @ParameterizedTest
  @ValueSource(strings = {"POSTTRAN", "POSTTRAN-EDGE", "POSTTRAN-EMPTY", "CBTRN01C", "TRANBKP"})
  void rerunGivesZeroDiff(String job) throws Exception {
    boolean hasCobc = onPath("cobc") && onPath("faketime");
    if ("true".equalsIgnoreCase(System.getenv("CARDDEMO_REQUIRE_COBOL"))) {
      assertThat(hasCobc).as("cobc + faketime on PATH").isTrue();
    }
    assumeTrue(hasCobc, "GnuCOBOL not installed");
    Process p =
        new ProcessBuilder("bash", "run-cobol.sh", job, "--verify")
            .directory(Golden.b1("").toFile())
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
