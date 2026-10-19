package com.carddemo.platform.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.platform.time.CeeDays;
import com.carddemo.platform.time.Csutldtc;
import com.carddemo.platform.time.FixedBusinessClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Parity of {@link Csutldtc}/{@link CeeDays} with the GnuCOBOL golden of the original {@code
 * CSUTLDTC} ({@code golden/1c-platform/CSUTLDTC}): every byte of {@code LS-RESULT} and {@code
 * RETURN-CODE}, case by case.
 */
@Tag("parity")
class CsutldtcGoldenParityTest {

  private static final int CASE_LRECL = 20;
  private static final int RESULT_LRECL = 104;

  private final Csutldtc csutldtc = new Csutldtc(new CeeDays(FixedBusinessClock.golden()));

  @Test
  void javaPortMatchesTheCobolGoldenByteForByte() throws Exception {
    Path job = GoldenFixtures.golden("CSUTLDTC");
    List<String> cases = new ArrayList<>();
    for (String line : Files.readAllLines(job.resolve("input/CASES.txt"))) {
      if (!line.isEmpty()) {
        cases.add(String.format("%-" + CASE_LRECL + "s", line));
      }
    }
    String results =
        new String(
            Files.readAllBytes(job.resolve("expected/RESULTS.dat")), StandardCharsets.ISO_8859_1);
    assertThat(results.length()).isEqualTo(cases.size() * (RESULT_LRECL + 1));
    assertThat(cases).hasSizeGreaterThanOrEqualTo(40);

    SoftAssertions softly = new SoftAssertions();
    for (int i = 0; i < cases.size(); i++) {
      String expected = results.substring(i * (RESULT_LRECL + 1), (i + 1) * (RESULT_LRECL + 1));
      String date = cases.get(i).substring(0, 10);
      String mask = cases.get(i).substring(10, 20);
      Csutldtc.Result actual = csutldtc.call(date, mask);
      String record =
          date + mask + actual.message() + String.format("%04d", actual.returnCode()) + "\n";
      softly.assertThat(record).as("case %d '%s' / '%s'", i + 1, date, mask).isEqualTo(expected);
    }
    softly.assertAll();
  }

  @Test
  void goldenCoversEveryReachableFeedback() throws Exception {
    String results =
        Files.readString(
            GoldenFixtures.golden("CSUTLDTC").resolve("expected/RESULTS.dat"),
            StandardCharsets.ISO_8859_1);
    for (String code : List.of("0000", "2507", "2508", "2513", "2517", "2518", "2520")) {
      assertThat(results).contains("Mesg Code: " + code);
    }
  }

  @Test
  void goldenIsReproducibleUnderGnuCobol() throws Exception {
    GoldenFixtures.verifyWithGnuCobol("CSUTLDTC");
  }
}
