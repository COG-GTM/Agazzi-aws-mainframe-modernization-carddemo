package com.carddemo.batch.datalifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.datalifecycle.support.Goldens;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Parity gate: every b4 golden master is reproduced by the legacy COBOL with zero diff. */
@Tag("parity")
class B4CobolGoldenParityTest {

  static Stream<String> fixtures() throws IOException {
    try (Stream<Path> dirs = Files.list(Goldens.b4Path(""))) {
      return dirs
          .filter(d -> Files.exists(d.resolve("job.yaml")))
          .map(d -> d.getFileName().toString())
          .sorted()
          .toList()
          .stream();
    }
  }

  @ParameterizedTest
  @MethodSource("fixtures")
  void cobolReproducesTheGoldenMaster(String fixture) throws Exception {
    Process p =
        new ProcessBuilder(
                "python3", "carddemo-batch-datalifecycle/tools/golden_b4.py", fixture, "--verify")
            .directory(Goldens.modernRoot().toFile())
            .redirectErrorStream(true)
            .start();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(p.waitFor()).as(out).isZero();
    assertThat(out).contains("zero diff");
  }
}
