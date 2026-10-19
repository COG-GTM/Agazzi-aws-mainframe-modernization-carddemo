package com.carddemo.platform.wait;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.contracts.port.WaitService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@code COBSWAIT}: {@code MOVE PARM-VALUE (X(8)) TO MVSWAIT-TIME (9(8) COMP)}. */
class CobsWaitTest {

  @ParameterizedTest(name = "''{0}'' -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        // WAITSTEP.jcl SYSIN card: only the first 8 bytes are accepted
        "00003600      VALUE IN CENTISECONDS|3600",
        "00000000|0",
        "99999999|99999999",
        "00000001|1",
        "'     500'|500",
        "'500     '|50000000",
        "''|0"
      })
  void parmToCentiseconds(String card, long centiseconds) {
    assertThat(CobsWait.parmToCentiseconds(card)).isEqualTo(centiseconds);
  }

  @Test
  void rejectsNonNumericParm() {
    assertThatThrownBy(() -> CobsWait.parmToCentiseconds("12AB5678"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void callsMvswaitThroughTheWaitService() throws Exception {
    List<Duration> waits = new ArrayList<>();
    WaitService recorder = waits::add;

    long waited = new CobsWait(recorder).run("00003600      VALUE IN CENTISECONDS");

    assertThat(waited).isEqualTo(3600);
    assertThat(waits).containsExactly(Duration.ofSeconds(36));
  }
}
