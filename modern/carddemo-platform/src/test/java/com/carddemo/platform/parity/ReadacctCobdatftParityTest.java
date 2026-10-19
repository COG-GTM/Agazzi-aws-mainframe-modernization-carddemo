package com.carddemo.platform.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.platform.time.CobolDateFormatter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Parity of {@link CobolDateFormatter} with {@code COBDATFT} as called by {@code CBACT01C}
 * (READACCT): {@code OUT-ACCT-REISSUE-DATE} of every {@code PSCOMP} record must equal the Java type
 * 2 → 2 conversion of the seed {@code ACCT-REISSUE-DATE}.
 */
@Tag("parity")
class ReadacctCobdatftParityTest {

  /** {@code OUT-ACCT-REC}: 9(11) X S9(10)V99 x3, X(10) x3, S9(10)V99, COMP-3 (7), X(10). */
  private static final int PSCOMP_LRECL = 107;

  private static final int ACCT_LRECL = 300;
  private static final int REISSUE_OFFSET = 68;

  @Test
  void reissueDateConversionMatchesTheCobolGolden() throws Exception {
    Path seed = GoldenFixtures.modernRoot().resolve("../app/data/ASCII/acctdata.txt").normalize();
    Map<String, String> reissueById = new HashMap<>();
    for (String line : Files.readAllLines(seed, StandardCharsets.ISO_8859_1)) {
      if (line.isBlank()) {
        continue;
      }
      String rec = String.format("%-" + ACCT_LRECL + "s", line);
      reissueById.put(rec.substring(0, 11), rec.substring(REISSUE_OFFSET, REISSUE_OFFSET + 10));
    }
    byte[] pscomp =
        Files.readAllBytes(GoldenFixtures.golden("READACCT").resolve("expected/PSCOMP.bin"));
    assertThat(pscomp.length % PSCOMP_LRECL).isZero();
    assertThat(pscomp.length / PSCOMP_LRECL).isEqualTo(reissueById.size());

    SoftAssertions softly = new SoftAssertions();
    for (int off = 0; off < pscomp.length; off += PSCOMP_LRECL) {
      String id = new String(pscomp, off, 11, StandardCharsets.ISO_8859_1);
      String cobol = new String(pscomp, off + REISSUE_OFFSET, 10, StandardCharsets.ISO_8859_1);
      CobolDateFormatter.Result java = CobolDateFormatter.convert('2', reissueById.get(id), '2');
      softly.assertThat(java.valid()).as("account %s", id).isTrue();
      softly.assertThat(java.outputDate().substring(0, 10)).as("account %s", id).isEqualTo(cobol);
    }
    softly.assertAll();
  }

  @Test
  void pinnedGoldenEqualsTheBatchReadacctGolden() throws Exception {
    Path pinned = GoldenFixtures.golden("READACCT").resolve("expected");
    Path batch =
        GoldenFixtures.modernRoot()
            .resolve("carddemo-parity/src/test/resources/golden/batch/READACCT/expected");
    for (String file : List.of("PSCOMP.bin", "ARRYPS.bin", "VBPS.bin", "RETURN-CODES.txt")) {
      assertThat(Files.readAllBytes(pinned.resolve(file)))
          .as(file)
          .isEqualTo(Files.readAllBytes(batch.resolve(file)));
    }
  }

  @Test
  void goldenIsReproducibleUnderGnuCobol() throws Exception {
    GoldenFixtures.verifyWithGnuCobol("READACCT");
  }
}
