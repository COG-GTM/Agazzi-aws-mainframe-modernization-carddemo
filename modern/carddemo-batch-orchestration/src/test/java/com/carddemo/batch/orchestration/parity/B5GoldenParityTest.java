package com.carddemo.batch.orchestration.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.orchestration.BatchTestBase;
import com.carddemo.batch.orchestration.RunLog;
import com.carddemo.batch.orchestration.core.JclReturnCodes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

/**
 * Gate di parità B5: the Java jobs against the GnuCOBOL golden masters in {@code
 * golden/b5-orchestration} (WAITSTEP*, INTRDRJ1) and the orchestration smoke of the batch chain.
 */
@Tag("parity")
class B5GoldenParityTest extends BatchTestBase {

  private static final Path GOLDEN =
      repoRoot().resolve("modern/carddemo-parity/src/test/resources/golden");
  private static final Path B5 = GOLDEN.resolve("b5-orchestration");

  private static String sysin(Path jobYaml) throws IOException {
    Matcher m = Pattern.compile("sysin: \"(.*)\\\\n\"").matcher(Files.readString(jobYaml));
    assertThat(m.find()).as("sysin in " + jobYaml).isTrue();
    return m.group(1);
  }

  /** {@code <STEP> <RC>} lines as written by the golden harness. */
  private static List<String> returnCodes(JobExecution e, List<String> goldenSteps) {
    List<String> out = new ArrayList<>();
    for (StepExecution s : e.getStepExecutions()) {
      if (goldenSteps.contains(s.getStepName())) {
        out.add(s.getStepName() + " " + JclReturnCodes.of(s.getExitStatus()));
      }
    }
    return out;
  }

  private static List<String> lines(Path p) throws IOException {
    return Files.readAllLines(p).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
  }

  @ParameterizedTest
  @ValueSource(strings = {"WAITSTEP", "WAITSTEP-EMPTY", "WAITSTEP-MALFORMED"})
  void waitstepMatchesCobswait(String golden) throws Exception {
    Path dir = B5.resolve(golden);
    JobExecution e =
        run(
            "WAITSTEP",
            params().addString("centiseconds", sysin(dir.resolve("job.yaml"))).toJobParameters());
    Matcher m =
        Pattern.compile("MVSWAIT CENTISECONDS (\\d+)")
            .matcher(Files.readString(dir.resolve("expected/SYSOUT.txt")));
    assertThat(m.find()).isTrue();
    long cs = Long.parseLong(m.group(1));
    assertThat(waits.waits).containsExactly(Duration.ofMillis(cs * 10));
    List<String> rc = lines(dir.resolve("expected/RETURN-CODES.txt"));
    assertThat(returnCodes(e, List.of("WAIT"))).containsExactlyElementsOf(rc);
  }

  @Test
  void intrdrj1ReproMatchesTheGolden() throws Exception {
    Path dir = B5.resolve("INTRDRJ1");
    // The harness catalog materialises the PS input as FB LRECL=80 records: same input here.
    StringBuilder fb = new StringBuilder();
    for (String rec : Files.readAllLines(dir.resolve("input/FTPTEST.txt"))) {
      fb.append(String.format("%-80s", rec)).append('\n');
    }
    Files.writeString(dataset("AWS.M2.CARDEMO.FTP.TEST"), fb);
    JobExecution e = run("INTRDRJ1");
    assertThat(Files.readAllBytes(dataset("AWS.M2.CARDEMO.FTP.TEST.BKUP")))
        .isEqualTo(Files.readAllBytes(dir.resolve("expected/FTPBKUP.dat")));
    assertThat(returnCodes(e, List.of("IDCAMS")))
        .containsExactlyElementsOf(lines(dir.resolve("expected/RETURN-CODES.txt")));
    JobExecution j2 =
        awaitEnd(step(e, "STEP01").getExecutionContext().getLong("submitted.executionId"));
    assertThat(Files.readAllBytes(dataset("AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR")))
        .isEqualTo(Files.readAllBytes(dir.resolve("expected/FTPBKUP.dat")));
    assertThat(JclReturnCodes.of(j2)).isZero();
  }

  /**
   * Smoke: each batch job of the chain is a stand-in that publishes its golden outputs; the cycle
   * must run them in the golden ORDER and leave every golden output in place, byte for byte.
   */
  @Test
  void fullBatchCycleRunsTheGoldenChainInOrder() throws Exception {
    Path fixture = B5.resolve("fullBatchCycle");
    List<String> expectedOrder = lines(fixture.resolve("expected/ORDER.txt"));
    List<String> goldenOrder = lines(GOLDEN.resolve("batch/ORDER"));
    // TRANBKP (IDCAMS REPRO, no COBOL program) has no GnuCOBOL golden: it only feeds COMBTRAN.
    List<String> withGolden =
        expectedOrder.stream()
            .filter(j -> Files.isDirectory(GOLDEN.resolve("batch").resolve(j)))
            .toList();
    assertThat(goldenOrder).containsSubsequence(withGolden);
    for (String job : withGolden) {
      Path expected = GOLDEN.resolve("batch").resolve(job).resolve("expected");
      Path target = dataset(job);
      RunLog.ON_RUN.put(job, () -> copyTree(expected, target));
    }
    JobExecution e = run("fullBatchCycle");
    assertThat(RunLog.runs()).containsExactlyElementsOf(expectedOrder);
    List<String> rcs =
        e.getStepExecutions().stream()
            .sorted(java.util.Comparator.comparing(StepExecution::getId))
            .map(s -> s.getStepName() + " " + JclReturnCodes.of(s.getExitStatus()))
            .toList();
    assertThat(rcs).containsExactlyElementsOf(lines(fixture.resolve("expected/RETURN-CODES.txt")));
    for (String job : withGolden) {
      Path expected = GOLDEN.resolve("batch").resolve(job).resolve("expected");
      try (Stream<Path> files = Files.list(expected)) {
        for (Path f : files.toList()) {
          assertThat(Files.readAllBytes(dataset(job).resolve(f.getFileName().toString())))
              .as(job + "/" + f.getFileName())
              .isEqualTo(Files.readAllBytes(f));
        }
      }
    }
  }

  private static void copyTree(Path from, Path to) {
    try (Stream<Path> files = Files.list(from)) {
      Files.createDirectories(to);
      for (Path f : files.toList()) {
        Files.copy(f, to.resolve(f.getFileName().toString()));
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
