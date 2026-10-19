package com.carddemo.batch.posting.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.posting.cbtrn02c.Cbtrn02cPoster;
import com.carddemo.batch.posting.cbtrn02c.RejectReason;
import com.carddemo.batch.posting.cbtrn02c.RejectRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;

class SupportTest {

  @TempDir Path dir;

  @Test
  void returnCodesMapToExitStatusesAndBack() {
    assertThat(ReturnCodes.exitStatus(0)).isEqualTo(ExitStatus.COMPLETED);
    assertThat(ReturnCodes.exitStatus(4).getExitCode()).isEqualTo("RC0004");
    assertThat(ReturnCodes.abend(new CobolAbendException("X", null)).getExitCode())
        .isEqualTo("ABEND-U0999");
    assertThat(ReturnCodes.abend(new CobolAbendException(12, "Y", null)).getExitCode())
        .isEqualTo("ABEND-RC0012");
    assertThat(ReturnCodes.processExitCode("COMPLETED")).isZero();
    assertThat(ReturnCodes.processExitCode("BYPASSED")).isZero();
    assertThat(ReturnCodes.processExitCode("NOOP")).isZero();
    assertThat(ReturnCodes.processExitCode("RC0004")).isEqualTo(4);
    assertThat(ReturnCodes.processExitCode("ABEND-RC0012")).isEqualTo(12);
    assertThat(ReturnCodes.processExitCode("ABEND-U0999")).isEqualTo(16);
    assertThat(ReturnCodes.processExitCode("FAILED")).isEqualTo(16);
    assertThat(ReturnCodes.processExitCode(null)).isEqualTo(16);
  }

  @Test
  void abendCarriesFileStatusLine() {
    CobolAbendException e = new CobolAbendException("E", "22");
    assertThat(e.abendCode()).isEqualTo(999);
    assertThat(e.fileStatusLine()).isEqualTo("FILE STATUS IS: NNNN0022");
    assertThat(new CobolAbendException("E", null).fileStatusLine()).isNull();
  }

  @Test
  void sysoutIsFramedLikeTheGoldenHarness() throws Exception {
    Sysout s = new Sysout("PGM");
    s.display("HELLO");
    s.appendTo(null, "STEP01", 0);
    Path out = dir.resolve("sub/SYSOUT.txt");
    s.appendTo(out, "STEP01", 4);
    assertThat(Files.readAllLines(out))
        .containsExactly("---- STEP STEP01 PGM=PGM", "HELLO", "---- RC=4");
    assertThat(s.lines()).containsExactly("HELLO");
  }

  @Test
  void rejectRecordLayout() {
    String enc = RejectRecord.of("X", RejectReason.OVERLIMIT).encode();
    assertThat(enc).hasSize(RejectRecord.LENGTH);
    assertThat(enc.substring(350, 354)).isEqualTo("0102");
    RejectRecord back = RejectRecord.decode(enc);
    assertThat(back.reason()).isEqualTo(102);
    assertThat(back.description().strip()).isEqualTo("OVERLIMIT TRANSACTION");
  }

  @Test
  void db2TimestampKeepsHundredths() {
    assertThat(Cbtrn02cPoster.db2Timestamp(LocalDateTime.of(2022, 7, 18, 9, 5, 7, 123_456_789)))
        .isEqualTo("2022-07-18-09.05.07.120000");
  }
}
