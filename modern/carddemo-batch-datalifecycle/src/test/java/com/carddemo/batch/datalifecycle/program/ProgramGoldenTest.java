package com.carddemo.batch.datalifecycle.program;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.datalifecycle.io.RecordFiles;
import com.carddemo.batch.datalifecycle.io.RecordFormat;
import com.carddemo.batch.datalifecycle.support.Goldens;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProgramGoldenTest {

  private static String framed(String step, String pgm, Sysout s, int rc) {
    try {
      java.nio.file.Files.writeString(
          java.nio.file.Path.of("target", "actual-" + pgm + ".txt"),
          s.text(),
          java.nio.charset.StandardCharsets.ISO_8859_1);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
    return "---- STEP " + step + " PGM=" + pgm + "\n" + s.text() + "---- RC=" + rc + "\n";
  }

  @Test
  void cbact01cMatchesReadacctGolden() {
    ByteArrayOutputStream ps = new ByteArrayOutputStream();
    ByteArrayOutputStream arr = new ByteArrayOutputStream();
    ByteArrayOutputStream vb = new ByteArrayOutputStream();
    Sysout sysout = new Sysout();
    long n =
        new Cbact01c()
            .run(
                () -> RecordFiles.open(Goldens.seed("acctdata.txt"), 300, 0, 11),
                () ->
                    new Cbact01c.Outputs(
                        RecordFiles.sink(ps, RecordFormat.FIXED),
                        RecordFiles.sink(arr, RecordFormat.FIXED),
                        RecordFiles.sink(vb, RecordFormat.VARIABLE)),
                sysout);
    assertThat(n).isEqualTo(50);
    assertThat(ps.toByteArray()).isEqualTo(Goldens.bytes(Goldens.batch("READACCT", "PSCOMP.bin")));
    assertThat(arr.toByteArray()).isEqualTo(Goldens.bytes(Goldens.batch("READACCT", "ARRYPS.bin")));
    assertThat(vb.toByteArray()).isEqualTo(Goldens.bytes(Goldens.batch("READACCT", "VBPS.bin")));
    assertThat(framed("STEP05", "CBACT01C", sysout, 0))
        .isEqualTo(Goldens.sysout(Goldens.batch("READACCT", "SYSOUT.txt")));
  }

  @ParameterizedTest
  @CsvSource({
    "READCARD,CBACT02C,carddata.txt,150,16",
    "READXREF,CBACT03C,cardxref.txt,50,16",
    "READCUST,CBCUS01C,custdata.txt,500,9"
  })
  void sequentialDumpsMatchGolden(String job, String pgm, String seed, int lrecl, int key) {
    SequentialDumpProgram program =
        switch (pgm) {
          case "CBACT02C" -> SequentialDumpProgram.CBACT02C;
          case "CBACT03C" -> SequentialDumpProgram.CBACT03C;
          default -> SequentialDumpProgram.CBCUS01C;
        };
    Sysout sysout = new Sysout();
    program.run(() -> RecordFiles.open(Goldens.seed(seed), lrecl, 0, key), sysout);
    assertThat(program.name()).isEqualTo(pgm);
    assertThat(framed("STEP05", pgm, sysout, 0))
        .isEqualTo(Goldens.sysout(Goldens.b4(job, "SYSOUT.txt")));
  }
}
