package com.carddemo.datamigration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.datamigration.export.VsamExporter;
import com.carddemo.datamigration.io.SourceDataset;
import com.carddemo.datamigration.io.SourceReader;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.SourceFormat;
import com.carddemo.datamigration.recon.DatasetMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Golden 1b-datamigration: decode → encode of every legacy extract is byte-identical, and the
 * reconciliation fingerprint (count, checksums, column hashes) is pinned in {@code
 * golden/1b-datamigration/vsamToRelational/metrics-<format>.json}. Regenerate with {@code
 * -Dgolden.update=true}.
 */
@Tag("parity")
class AsciiRoundTripParityTest {

  @ParameterizedTest
  @EnumSource(SourceFormat.class)
  void extractRoundTripAndMetricsMatchGolden(SourceFormat format) throws Exception {
    Path dir = TestPaths.appData(format == SourceFormat.ASCII ? "ASCII" : "EBCDIC");
    Map<Dataset, SourceDataset> src =
        new SourceReader().readAll(dir, format, List.of(Dataset.values()));
    assertThat(src).isNotEmpty();
    Map<String, Object> metrics = new LinkedHashMap<>();
    for (SourceDataset s : src.values()) {
      assertThat(s.decodeErrors()).as(s.file()).isEmpty();
      if (format != SourceFormat.EXPORT) {
        List<byte[]> enc =
            s.records().stream()
                .map(r -> s.dataset().codec(format == SourceFormat.EBCDIC).encode(r.value()))
                .toList();
        assertThat(s.layout().join(enc))
            .as("byte round trip " + s.file())
            .isEqualTo(Files.readAllBytes(dir.resolve(s.file())));
        if (format == SourceFormat.ASCII) {
          assertThat(VsamExporter.encode(s.dataset(), s.records(), false))
              .as("VSAM key order " + s.file())
              .containsExactlyElementsOf(enc);
        }
      }
      DatasetMetrics m = DatasetMetrics.of(s.dataset(), s.records());
      Map<String, Object> e = new LinkedHashMap<>();
      e.put("file", s.file());
      e.put("count", m.count());
      Map<String, String> sums = new LinkedHashMap<>();
      m.sums().forEach((k, v) -> sums.put(k, v.toPlainString()));
      e.put("checksums", sums);
      e.put("recordHash", m.recordHash());
      e.put("columnHashes", m.columnHashes());
      metrics.put(s.dataset().name(), e);
    }
    ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    Path golden =
        TestPaths.golden()
            .resolve("vsamToRelational/metrics-" + format.name().toLowerCase() + ".json");
    String actual = om.writeValueAsString(metrics) + "\n";
    if (Boolean.getBoolean("golden.update")) {
      Files.createDirectories(golden.getParent());
      Files.writeString(golden, actual);
    }
    assertThat(actual).isEqualTo(Files.readString(golden));
  }

  @ParameterizedTest
  @EnumSource(
      value = SourceFormat.class,
      names = {"EBCDIC", "EXPORT"})
  void checksumsAgreeWithAscii(SourceFormat format) {
    var reader = new SourceReader();
    var ascii =
        reader.readAll(TestPaths.appData("ASCII"), SourceFormat.ASCII, List.of(Dataset.values()));
    var other = reader.readAll(TestPaths.appData("EBCDIC"), format, List.of(Dataset.values()));
    for (Dataset ds : List.of(Dataset.ACCTDATA, Dataset.TCATBALF, Dataset.DALYTRAN)) {
      if (!ascii.containsKey(ds) || !other.containsKey(ds)) {
        continue;
      }
      Map<String, BigDecimal> a = DatasetMetrics.of(ds, ascii.get(ds).records()).sums();
      Map<String, BigDecimal> o = DatasetMetrics.of(ds, other.get(ds).records()).sums();
      if (format == SourceFormat.EXPORT && ds == Dataset.ACCTDATA) {
        // CBEXPORT snapshot carries ACCT-CURR-BAL 0 for accounts 1 and 50 (194.00 + 492.00).
        assertThat(a.get("ACCT-CURR-BAL").subtract(o.get("ACCT-CURR-BAL")))
            .isEqualByComparingTo("686.00");
        a = new LinkedHashMap<>(a);
        a.put("ACCT-CURR-BAL", o.get("ACCT-CURR-BAL"));
      }
      assertThat(o).as(format + " " + ds).isEqualTo(a);
    }
  }
}
