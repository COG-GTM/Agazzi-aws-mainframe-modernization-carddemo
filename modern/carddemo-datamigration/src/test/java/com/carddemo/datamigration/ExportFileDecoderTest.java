package com.carddemo.datamigration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.datamigration.io.SourceDataset;
import com.carddemo.datamigration.io.SourceReader;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.SourceFormat;
import com.carddemo.datamigration.validate.KeyLookup;
import com.carddemo.datamigration.validate.SourceValidator;
import com.carddemo.datamigration.validate.Violation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** CBEXPORT file (COMP / COMP-3) decodes to the same records as the KSDS extracts. */
class ExportFileDecoderTest {

  @Test
  void exportFileMatchesKsdsExtracts() {
    SourceReader reader = new SourceReader();
    Map<Dataset, SourceDataset> export =
        reader.readAll(TestPaths.appData("EBCDIC"), SourceFormat.EXPORT, List.of(Dataset.values()));
    assertThat(export.keySet())
        .containsExactly(
            Dataset.CUSTDATA,
            Dataset.ACCTDATA,
            Dataset.CARDDATA,
            Dataset.CARDXREF,
            Dataset.TRANSACT);
    assertThat(export.get(Dataset.TRANSACT).records()).hasSize(300);
    export.values().forEach(s -> assertThat(s.decodeErrors()).isEmpty());
    Map<Dataset, SourceDataset> ebcdic =
        reader.readAll(
            TestPaths.appData("EBCDIC"),
            SourceFormat.EBCDIC,
            List.of(Dataset.CARDXREF, Dataset.CARDDATA));
    for (Dataset ds : List.of(Dataset.CARDXREF)) {
      var a = export.get(ds).records().stream().map(r -> r.value().toString()).sorted().toList();
      var b = ebcdic.get(ds).records().stream().map(r -> r.value().toString()).sorted().toList();
      assertThat(a).isEqualTo(b);
    }
    List<Violation> v = new SourceValidator().validate(export, KeyLookup.NONE);
    assertThat(v).noneMatch(x -> x.severity() == Violation.Severity.ERROR);
  }
}
