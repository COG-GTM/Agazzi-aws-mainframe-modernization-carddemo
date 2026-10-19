package com.carddemo.batch.reports.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

class SupportTest {

  @TempDir Path dir;

  @Test
  void sortIncludesSortsStablyAndReformats() {
    List<String> in = List.of("B2x", "A1y", "B1z", "C3w", "A");
    assertThat(
            RecordSort.sort(
                in,
                List.of(new RecordSort.Include(1, 1, "NE", "C")),
                List.of(new RecordSort.Key(1, 1, false)),
                List.of(),
                3))
        .containsExactly("B2x", "B1z", "A1y", "A");
    assertThat(
            RecordSort.sort(
                in,
                List.of(
                    new RecordSort.Include(2, 1, "GT", "1"),
                    new RecordSort.Include(2, 1, "LT", "3")),
                List.of(),
                List.of(new RecordSort.Outrec(1, 3, 1), new RecordSort.Outrec(2, 1, 2)),
                4))
        .containsExactly("xB2 ");
    assertThat(
            RecordSort.sort(
                in, List.of(new RecordSort.Include(1, 1, "EQ", "A")), List.of(), List.of(), 3))
        .containsExactly("A1y", "A");
    assertThat(
            RecordSort.sort(
                in,
                List.of(
                    new RecordSort.Include(1, 1, "GE", "B"),
                    new RecordSort.Include(1, 1, "LE", "B")),
                List.of(new RecordSort.Key(2, 1, true)),
                List.of(),
                3))
        .containsExactly("B1z", "B2x");
    assertThatThrownBy(
            () ->
                RecordSort.sort(
                    in, List.of(new RecordSort.Include(1, 1, "XX", "A")), List.of(), List.of(), 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ksdsKeepsKeyOrderAndRejectsDuplicates() {
    Ksds k = Ksds.load("T", List.of("02b", "01a"), 0, 2);
    assertThat(k.inKeyOrder()).containsExactly("01a", "02b");
    assertThat(k.read("01")).isEqualTo("01a");
    assertThat(k.read("09")).isNull();
    assertThat(k.size()).isEqualTo(2);
    assertThatThrownBy(() -> Ksds.load("T", List.of("01a", "01b"), 0, 2))
        .isInstanceOf(JobAbendException.class)
        .hasMessageContaining("duplicate key 01");
  }

  @Test
  void fixedRecordsPadTruncateAndSkipBlankLines() throws Exception {
    Path f = dir.resolve("sub/F.dat");
    FixedRecords.write(f, List.of("abc", "toolong"), 4);
    assertThat(Files.readString(f)).isEqualTo("abc \ntool\n");
    Files.writeString(dir.resolve("G.dat"), "x\r\n\nyy\n");
    assertThat(FixedRecords.read(dir.resolve("G.dat"), 2)).containsExactly("x ", "yy");
    assertThat(FixedRecords.read(dir.resolve("missing.dat"), 2)).isEmpty();
    assertThat(FixedRecords.pad(null, 2)).isEqualTo("  ");
  }

  @Test
  void stringDelimitedBy() {
    assertThat(CobolText.delimitedBy("New York  ", " ")).isEqualTo("New");
    assertThat(CobolText.delimitedBy("abc", "  ")).isEqualTo("abc");
    assertThat(CobolText.stringInto(5, "ab", "cdef")).isEqualTo("abcde");
  }

  @Test
  void catalogResolvesDdsAndOverrides() {
    JobParameters p =
        new JobParametersBuilder()
            .addString(DatasetCatalog.DATASET_DIR, dir.toString())
            .addString("dsn.TRANREPT", "/x/report.txt")
            .toJobParameters();
    DatasetCatalog c = DatasetCatalog.from(p);
    assertThat(c.path("TRANSACT")).isEqualTo(dir.resolve("TRANSACT.dat"));
    assertThat(c.path("TRANREPT")).isEqualTo(Path.of("/x/report.txt"));
    assertThatThrownBy(() -> DatasetCatalog.from(new JobParameters()))
        .isInstanceOf(IllegalArgumentException.class);
    JobParameters db =
        new JobParametersBuilder()
            .addString(DatasetCatalog.DATASET_DIR, dir.toString())
            .addString(DatasetSource.SOURCE, "db")
            .toJobParameters();
    assertThatThrownBy(() -> DatasetSource.from(db, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void exitStatusToReturnCode() {
    assertThat(AbendExitStatus.returnCode(ExitStatus.COMPLETED)).isZero();
    assertThat(AbendExitStatus.returnCode(new ExitStatus(AbendExitStatus.ABEND))).isEqualTo(16);
    assertThat(AbendExitStatus.returnCode(ExitStatus.FAILED)).isEqualTo(12);
    assertThat(new JobAbendException("P", "m").program()).isEqualTo("P");
  }

  @Test
  void snakeCaseColumns() {
    assertThat(JdbcDatasets.snake("custAddrLine1")).isEqualTo("cust_addr_line_1");
    assertThat(JdbcDatasets.snake("custDobYyyyMmDd")).isEqualTo("cust_dob_yyyy_mm_dd");
    assertThat(JdbcDatasets.supports("TRANSACT")).isTrue();
    assertThat(JdbcDatasets.supports("DATEPARM")).isFalse();
  }
}
