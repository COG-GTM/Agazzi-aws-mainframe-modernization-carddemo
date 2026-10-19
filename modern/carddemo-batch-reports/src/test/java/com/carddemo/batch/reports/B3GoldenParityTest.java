package com.carddemo.batch.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.reports.pdf.TextToPdf;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Golden parity gate of b3-reports: each scenario of {@code golden/b3-reports} (captured with
 * GnuCOBOL by {@code tools/run-cobol-b3.sh}) is run through the Spring Batch job and every output
 * is compared byte for byte, together with the step return codes.
 */
@Tag("parity")
@SpringBootTest(classes = ReportsTestApplication.class)
class B3GoldenParityTest {

  @Autowired JobLauncher launcher;

  @Autowired
  @Qualifier("TRANREPT")
  Job tranrept;

  @Autowired
  @Qualifier("CREASTMT")
  Job creastmt;

  @Autowired
  @Qualifier("TXT2PDF1")
  Job txt2pdf1;

  @TempDir Path dir;

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "TRANREPT,TRANREPT,TRANDALY;TRANREPT",
    "TRANREPT-NODATA,TRANREPT,TRANDALY;TRANREPT",
    "CREASTMT,CREASTMT,STMTPS;STMTHTML",
    "CREASTMT-EMPTY,CREASTMT,STMTPS;STMTHTML"
  })
  void scenarioMatchesCobolGolden(String scenario, String jobName, String outputs)
      throws Exception {
    B3Fixtures.materialise(scenario, dir);
    Job job = jobName.equals("TRANREPT") ? tranrept : creastmt;
    JobExecution e = launcher.run(job, B3Fixtures.params(dir, "scenario", scenario));

    Map<String, Integer> golden = B3Fixtures.goldenReturnCodes(scenario);
    Map<String, Integer> actual =
        B3Fixtures.returnCodes(e).entrySet().stream()
            .filter(x -> golden.containsKey(x.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(actual).isEqualTo(golden);

    for (String dd : outputs.split(";")) {
      Path expected = B3Fixtures.B3.resolve(scenario).resolve("expected/" + dd + ".dat");
      assertThat(B3Fixtures.lines(dir.resolve(dd + ".dat")))
          .as("%s %s", scenario, dd)
          .containsExactlyElementsOf(B3Fixtures.lines(expected));
      assertThat(B3Fixtures.bytes(dir.resolve(dd + ".dat")))
          .as("%s %s bytes", scenario, dd)
          .isEqualTo(B3Fixtures.bytes(expected));
    }
  }

  @Test
  void txt2pdfTextMatchesStatementGolden() throws Exception {
    Path stmt = B3Fixtures.B3.resolve("CREASTMT/expected/STMTPS.dat");
    java.nio.file.Files.copy(stmt, dir.resolve("STMTPS.dat"));
    JobExecution e = launcher.run(txt2pdf1, B3Fixtures.params(dir));
    assertThat(e.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

    List<String> extracted = TextToPdf.extractLines(B3Fixtures.bytes(dir.resolve("STMTPDF.dat")));
    List<String> expected =
        B3Fixtures.lines(stmt).stream()
            .filter(l -> !l.isBlank())
            .map(B3GoldenParityTest::norm)
            .toList();
    assertThat(extracted.stream().map(B3GoldenParityTest::norm).toList())
        .containsExactlyElementsOf(expected);
  }

  private static String norm(String s) {
    return s.strip().replaceAll("\\s+", " ");
  }
}
