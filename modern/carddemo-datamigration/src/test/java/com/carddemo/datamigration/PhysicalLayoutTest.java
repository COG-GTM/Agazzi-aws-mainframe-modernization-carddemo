package com.carddemo.datamigration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.datamigration.io.MalformedSourceException;
import com.carddemo.datamigration.io.PhysicalLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PhysicalLayoutTest {

  private static byte[] b(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }

  @Test
  void crlfWithLfOnLastRecordIsRebuiltByteIdentical() {
    byte[] data = b("AAAA\r\nBBBB\r\nCCCC\n");
    PhysicalLayout.Lines l = PhysicalLayout.splitLines(data, 4);
    assertThat(l.layout().recordTerminator()).isEqualTo("\r\n");
    assertThat(l.layout().finalTerminator()).isEqualTo("\n");
    assertThat(l.layout().join(l.records())).isEqualTo(data);
    assertThat(PhysicalLayout.decode(l.layout().encode())).isEqualTo(l.layout());
  }

  @Test
  void rightTrimmedRecordsArePaddedAndTrimmedBack() {
    byte[] data = b("AB\nABCD\nA\n");
    PhysicalLayout.Lines l = PhysicalLayout.splitLines(data, 6);
    assertThat(l.layout().rightTrimmed()).isTrue();
    assertThat(new String(l.records().get(0), StandardCharsets.US_ASCII)).isEqualTo("AB    ");
    assertThat(l.layout().join(l.records())).isEqualTo(data);
  }

  @Test
  void noFinalSeparatorIsKept() {
    byte[] data = b("AB\nCD");
    PhysicalLayout.Lines l = PhysicalLayout.splitLines(data, 2);
    assertThat(l.layout().finalTerminator()).isEmpty();
    assertThat(l.layout().join(l.records())).isEqualTo(data);
  }

  @Test
  void malformedFilesAreRejected() {
    assertThatThrownBy(() -> PhysicalLayout.splitLines(b("ABC\n"), 2))
        .isInstanceOf(MalformedSourceException.class);
    assertThatThrownBy(() -> PhysicalLayout.splitLines(b("AB\r\nCD\nEF\n"), 2))
        .isInstanceOf(MalformedSourceException.class);
    assertThatThrownBy(() -> PhysicalLayout.splitFixed(new byte[5], 2))
        .isInstanceOf(MalformedSourceException.class);
  }

  @Test
  void fixedRoundTrip() {
    byte[] data = b("ABCDEF");
    assertThat(PhysicalLayout.FIXED.join(PhysicalLayout.splitFixed(data, 3))).isEqualTo(data);
  }
}
