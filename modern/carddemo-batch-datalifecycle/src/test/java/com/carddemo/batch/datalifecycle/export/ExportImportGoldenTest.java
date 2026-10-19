package com.carddemo.batch.datalifecycle.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.datalifecycle.io.RecordFiles;
import com.carddemo.batch.datalifecycle.io.RecordFormat;
import com.carddemo.batch.datalifecycle.program.Sysout;
import com.carddemo.batch.datalifecycle.support.Goldens;
import com.carddemo.platform.time.FixedBusinessClock;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExportImportGoldenTest {

  @Test
  void cbexportMatchesGolden() {
    ByteArrayOutputStream exp = new ByteArrayOutputStream();
    Sysout sysout = new Sysout();
    CbExport.Counts counts =
        new CbExport(FixedBusinessClock.golden())
            .run(
                new CbExport.Inputs(
                    () -> RecordFiles.open(Goldens.seed("custdata.txt"), 500, 0, 9),
                    () -> RecordFiles.open(Goldens.seed("acctdata.txt"), 300, 0, 11),
                    () -> RecordFiles.open(Goldens.seed("cardxref.txt"), 50, 0, 16),
                    () -> RecordFiles.open(Goldens.seed("dailytran.txt"), 350, 0, 16),
                    () -> RecordFiles.open(Goldens.seed("carddata.txt"), 150, 0, 16)),
                () -> RecordFiles.sink(exp, RecordFormat.FIXED_LF),
                sysout);
    assertThat(counts.total()).isEqualTo(500);
    assertThat(exp.toByteArray()).isEqualTo(Goldens.bytes(Goldens.b4("CBEXPORT", "EXPDATA.dat")));
    assertThat("---- STEP STEP02 PGM=CBEXPORT\n" + sysout.text() + "---- RC=0\n")
        .isEqualTo(Goldens.sysout(Goldens.b4("CBEXPORT", "SYSOUT.txt")));
  }

  @ParameterizedTest
  @CsvSource({
    "CBIMPORT, CBEXPORT/expected/EXPDATA.dat, 500",
    "CBIMPORT-REJECT, CBIMPORT-REJECT/input/EXPREJ.dat, 6"
  })
  void cbimportMatchesGolden(String fixture, String input, long read) {
    Map<String, ByteArrayOutputStream> out = new LinkedHashMap<>();
    for (String dd :
        new String[] {"CUSTOUT", "ACCTOUT", "XREFOUT", "TRNXOUT", "CARDOUT", "ERROUT"}) {
      out.put(dd, new ByteArrayOutputStream());
    }
    Sysout sysout = new Sysout();
    CbImport.Counts c =
        new CbImport(FixedBusinessClock.golden())
            .run(
                () -> RecordFiles.open(Goldens.b4Path(input), 500, 27, 4),
                new CbImport.Outputs(
                    () -> RecordFiles.sink(out.get("CUSTOUT"), RecordFormat.FIXED_LF),
                    () -> RecordFiles.sink(out.get("ACCTOUT"), RecordFormat.FIXED_LF),
                    () -> RecordFiles.sink(out.get("XREFOUT"), RecordFormat.FIXED_LF),
                    () -> RecordFiles.sink(out.get("TRNXOUT"), RecordFormat.FIXED_LF),
                    () -> RecordFiles.sink(out.get("CARDOUT"), RecordFormat.FIXED_LF),
                    () -> RecordFiles.sink(out.get("ERROUT"), RecordFormat.FIXED_LF)),
                sysout);
    assertThat(c.read()).isEqualTo(read);
    out.forEach(
        (dd, bytes) ->
            assertThat(bytes.toByteArray())
                .as(dd)
                .isEqualTo(Goldens.bytes(Goldens.b4(fixture, dd + ".dat"))));
    assertThat("---- STEP STEP01 PGM=CBIMPORT\n" + sysout.text() + "---- RC=0\n")
        .isEqualTo(Goldens.sysout(Goldens.b4(fixture, "SYSOUT.txt")));
  }
}
