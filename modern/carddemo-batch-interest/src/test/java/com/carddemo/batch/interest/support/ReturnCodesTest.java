package com.carddemo.batch.interest.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;

class ReturnCodesTest {

  @TempDir Path tmp;

  @Test
  void exitStatusRoundTrip() {
    assertThat(ReturnCodes.exitStatus(0, "x")).isEqualTo(ExitStatus.COMPLETED);
    assertThat(ReturnCodes.exitStatus(16, null).getExitCode()).isEqualTo("RC16");
    assertThat(ReturnCodes.exitStatus(12, "IDCAMS").getExitDescription()).isEqualTo("IDCAMS");
    assertThat(ReturnCodes.of(ExitStatus.COMPLETED)).isZero();
    assertThat(ReturnCodes.of(ExitStatus.NOOP)).isZero();
    assertThat(ReturnCodes.of(new ExitStatus("RC12"))).isEqualTo(12);
    assertThat(ReturnCodes.of(new ExitStatus("RC04"))).isEqualTo(4);
    assertThat(ReturnCodes.of(new ExitStatus("RCxx"))).isEqualTo(16);
    assertThat(ReturnCodes.of(ExitStatus.FAILED)).isEqualTo(16);
  }

  @Test
  void abendsMapToTheirReturnCode() {
    assertThat(ReturnCodes.of(Abend.user999("x"))).isEqualTo(16);
    assertThat(Abend.user999("x").abendCode()).isEqualTo(999);
    Abend idcams = new Abend(12, "IDC", new IllegalStateException());
    assertThat(ReturnCodes.of(new RuntimeException("wrapped", idcams))).isEqualTo(12);
    assertThat(idcams.abendCode()).isZero();
    assertThat(ReturnCodes.of(new IllegalStateException())).isEqualTo(16);
  }

  @Test
  void sysoutAppendsToTheSysoutFile() throws Exception {
    Sysout sysout = new Sysout();
    Path file = tmp.resolve("spool/SYSOUT.txt");
    sysout.display("A");
    assertThat(sysout.lines()).containsExactly("A");
    sysout.flush(file.toString());
    sysout.display("B");
    sysout.flush(file.toString());
    sysout.display("C");
    sysout.flush(null);
    sysout.display("D");
    sysout.reset();
    assertThat(sysout.lines()).isEmpty();
    assertThat(Files.readAllLines(file)).containsExactly("A", "B");
  }
}
