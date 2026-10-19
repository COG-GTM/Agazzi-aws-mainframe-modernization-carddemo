package com.carddemo.ext.authorization.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.ext.authorization.MutableBusinessClock;
import com.carddemo.ext.authorization.batch.PendingAuthPurgeService;
import com.carddemo.ext.authorization.batch.PendingAuthUnloadLoadService;
import com.carddemo.ext.authorization.batch.PendingAuthUnloadLoadService.LoadReport;
import com.carddemo.ext.authorization.batch.PendingAuthUnloadLoadService.Unload;
import com.carddemo.ext.authorization.batch.PurgeParameters;
import com.carddemo.ext.authorization.batch.PurgeReport;
import com.carddemo.ext.authorization.codec.PackedDecimal;
import com.carddemo.ext.authorization.store.AuthFraudRepository;
import com.carddemo.ext.authorization.store.PendingAuthDetailRepository;
import com.carddemo.ext.authorization.store.PendingAuthSummaryRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Golden parity for the X1 batch jobs: DBPAUTP0 (HD unload IMSDATA.DBPAUTP0.dat -> tables) and
 * CBPAUP0J (CBPAUP0C purge run by GnuCOBOL with a DL/I stub, see src/test/cobol/run-x1-golden.sh).
 * The raw unload carries one root with key x'404040404040' that cannot be an ACCNTID: the Java
 * loader rejects it, so it is filtered out of the COBOL expectation (PR: parity deviation X1-1).
 */
@Tag("parity")
@SpringBootTest
class X1BatchParityTest {

  static final Path GOLDEN =
      Path.of("../carddemo-parity/src/test/resources/golden/x1-authorization");
  static final Path RAW =
      Path.of(
          "../../app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat");

  @Autowired PendingAuthUnloadLoadService unloadLoad;
  @Autowired PendingAuthPurgeService purge;
  @Autowired PendingAuthDetailRepository details;
  @Autowired PendingAuthSummaryRepository summaries;
  @Autowired AuthFraudRepository frauds;
  @Autowired MutableBusinessClock clock;

  @BeforeEach
  void clear() {
    frauds.deleteAll();
    details.deleteAll();
    summaries.deleteAll();
  }

  static byte[] validRoots(byte[] roots) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int o = 0; o + 100 <= roots.length; o += 100) {
      if (PackedDecimal.isNumeric(roots, o, 11)) {
        out.write(roots, o, 100);
      }
    }
    return out.toByteArray();
  }

  /** Deviation X1-2: LOW-VALUES in PIC X (PA-AUTH-STATUS, PA-ACCOUNT-STATUS) persist as spaces. */
  static byte[] lowValuesAsSpaces(byte[] roots) {
    byte[] b = roots.clone();
    for (int o = 0; o + 100 <= b.length; o += 100) {
      for (int i = o + 15; i < o + 26; i++) {
        if (b[i] == 0) {
          b[i] = ' ';
        }
      }
    }
    return b;
  }

  static int invalidRoots(byte[] roots) {
    return roots.length / 100 - validRoots(roots).length / 100;
  }

  static void assertSegments(String what, byte[] actual, byte[] expected, int len) {
    assertThat(actual.length).as(what + " length").isEqualTo(expected.length);
    HexFormat hex = HexFormat.of();
    for (int o = 0; o < expected.length; o += len) {
      assertThat(hex.formatHex(actual, o, o + len))
          .as("%s record %d", what, o / len)
          .isEqualTo(hex.formatHex(expected, o, o + len));
    }
  }

  @Test
  void dbpautp0LoadsTheHdUnloadAndUnloadsTheSameSegments() throws IOException {
    byte[] roots = Files.readAllBytes(GOLDEN.resolve("DBPAUTP0/expected/PAUTSUM0.bin"));
    byte[] kids = Files.readAllBytes(GOLDEN.resolve("DBPAUTP0/expected/PAUTDTL1.bin"));
    LoadReport r = unloadLoad.loadHdUnload(Files.readAllBytes(RAW));
    assertThat(r.rootsLoaded()).isEqualTo(roots.length / 100 - invalidRoots(roots));
    assertThat(r.childrenLoaded()).isEqualTo(kids.length / 206);
    assertThat(r.rejected()).isEqualTo(invalidRoots(roots));
    Unload u = unloadLoad.unload(StandardCharsets.ISO_8859_1);
    assertSegments("PAUTSUM0", u.roots(), lowValuesAsSpaces(validRoots(roots)), 100);
    assertSegments("PAUTDTL1", u.children(), kids, 206);
  }

  static Stream<Arguments> scenarios() throws IOException {
    try (Stream<Path> s = Files.list(GOLDEN.resolve("CBPAUP0J"))) {
      return s
          .filter(Files::isDirectory)
          .sorted()
          .map(p -> Arguments.of(p.getFileName().toString()))
          .toList()
          .stream();
    }
  }

  static long count(String sysout, String label) {
    Matcher m = Pattern.compile(Pattern.quote(label) + "\\s*:\\+?(\\d+)").matcher(sysout);
    assertThat(m.find()).as(label).isTrue();
    return Long.parseLong(m.group(1));
  }

  @ParameterizedTest(name = "CBPAUP0J {0}")
  @MethodSource("scenarios")
  void cbpaup0jPurgeMatchesCobol(String scenario) throws IOException {
    Path dir = GOLDEN.resolve("CBPAUP0J").resolve(scenario);
    byte[] inRoots = Files.readAllBytes(GOLDEN.resolve("DBPAUTP0/expected/PAUTSUM0.bin"));
    byte[] inKids = Files.readAllBytes(GOLDEN.resolve("DBPAUTP0/expected/PAUTDTL1.bin"));
    unloadLoad.load(new Unload(inRoots, inKids), StandardCharsets.ISO_8859_1);
    clock.set(
        LocalDate.parse(Files.readString(dir.resolve("BUSINESS-DATE.txt")).trim()).atTime(12, 0));
    String sysin = Files.readString(dir.resolve("SYSIN.txt")).replace("\n", "");
    PurgeReport rep = purge.purge(PurgeParameters.parse(sysin));

    byte[] expRoots = Files.readAllBytes(dir.resolve("expected/PAUTSUM0.bin"));
    byte[] expKids = Files.readAllBytes(dir.resolve("expected/PAUTDTL1.bin"));
    Unload u = unloadLoad.unload(StandardCharsets.ISO_8859_1);
    assertSegments("PAUTSUM0", u.roots(), lowValuesAsSpaces(validRoots(expRoots)), 100);
    assertSegments("PAUTDTL1", u.children(), expKids, 206);

    String sysout =
        Files.readString(dir.resolve("expected/SYSOUT.txt"), StandardCharsets.ISO_8859_1);
    int bad = invalidRoots(inRoots);
    int badDeleted = bad - invalidRoots(expRoots);
    assertThat(rep.summaryRead()).isEqualTo(count(sysout, "# TOTAL SUMMARY READ") - bad);
    assertThat(rep.summaryDeleted()).isEqualTo(count(sysout, "# SUMMARY REC DELETED") - badDeleted);
    assertThat(rep.detailRead()).isEqualTo(count(sysout, "# TOTAL DETAILS READ"));
    assertThat(rep.detailDeleted()).isEqualTo(count(sysout, "# DETAILS REC DELETED"));
    assertThat(String.format("%05d", rep.currentYyddd()))
        .isEqualTo(String.valueOf(count(sysout, "TODAYS DATE")));
    assertThat(Files.readString(dir.resolve("expected/RETURN-CODES.txt")).trim())
        .isEqualTo("CBPAUP0C 0");
  }
}
