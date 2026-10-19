package com.carddemo.parity.compare;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.copybook.CoreCodecs;
import com.carddemo.copybook.ZonedRecordCodec;
import com.carddemo.parity.ParityPaths;
import com.carddemo.parity.batch.GoldenJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordComparatorTest {

  private static List<String> accounts() throws Exception {
    GoldenJob job = GoldenJob.load(ParityPaths.modernRoot(), "POSTTRAN");
    return RecordComparator.readRecords(job.expectedOutput("ACCTDATA"), 300);
  }

  private static List<String> withBalance(List<String> recs, int index, String zoned) {
    ZonedRecordCodec.FieldLayout f =
        CoreCodecs.ACCOUNT.fields().stream()
            .filter(x -> x.name().equals("ACCT-CURR-BAL"))
            .findFirst()
            .orElseThrow();
    List<String> out = new ArrayList<>(recs);
    String r = out.get(index);
    out.set(index, r.substring(0, f.offset()) + zoned + r.substring(f.offset() + f.length()));
    return out;
  }

  @Test
  void reportsTheChangedFieldWithDecodedValues() throws Exception {
    List<String> exp = accounts();
    List<String> act = withBalance(exp, 2, "00000001234{");
    List<FieldDiff> diffs =
        new RecordComparator(ParityExceptions.none())
            .compare("POSTTRAN", "ACCTDATA", "CVACT01Y", exp, act);
    assertThat(diffs).hasSize(1);
    assertThat(diffs.get(0).record()).isEqualTo(3);
    assertThat(diffs.get(0).field()).isEqualTo("ACCT-CURR-BAL");
    assertThat(diffs.get(0).actual()).isEqualTo("123.40");
  }

  @Test
  void registeredExceptionsAreIgnoredAndCountsCompared() throws Exception {
    List<String> exp = accounts();
    List<String> act = withBalance(exp, 0, "00000000000{");
    var pe =
        new ParityExceptions(
            List.of(new ParityExceptions.Entry("PE-999", "POSTTRAN", "ACCT-CURR-BAL")));
    assertThat(new RecordComparator(pe).compare("POSTTRAN", "ACCTDATA", "CVACT01Y", exp, act))
        .isEmpty();
    assertThat(new RecordComparator(pe).compare("INTCALC", "ACCTDATA", "CVACT01Y", exp, act))
        .hasSize(1);
    assertThat(
            new RecordComparator(pe)
                .compare("POSTTRAN", "ACCTDATA", "CVACT01Y", exp, exp.subList(0, 49)))
        .extracting(FieldDiff::field)
        .containsExactly("RECORD-COUNT");
  }

  @Test
  void layoutsWithoutCodecAreComparedAsWholeRecord() {
    var d =
        new RecordComparator(ParityExceptions.none())
            .compare("POSTTRAN", "DALYREJS", "DALYREJS", List.of("A"), List.of("B"));
    assertThat(d).extracting(FieldDiff::field).containsExactly("RECORD");
  }

  @Test
  void registryFileIsParsed(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
    assertThat(
            ParityExceptions.load(ParityPaths.modernRoot().resolve("PARITY_EXCEPTIONS.md"))
                .entries())
        .extracting(ParityExceptions.Entry::id)
        .contains("PE-001");
    Path md = tmp.resolve("pe.md");
    Files.writeString(
        md,
        "| ID | P | F |\n|---|---|---|\n| PE-007 | * | `TRAN-AMT` | a | b | c | d | approved |\n");
    assertThat(ParityExceptions.load(md).entries())
        .containsExactly(new ParityExceptions.Entry("PE-007", "*", "TRAN-AMT"));
  }
}
