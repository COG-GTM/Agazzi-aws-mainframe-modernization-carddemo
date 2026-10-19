package com.carddemo.ext.authorization.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class PackedDecimalTest {

  @Test
  void encodesComp3WithSignNibble() {
    byte[] b = new byte[6];
    PackedDecimal.encode(new BigDecimal("1234.56"), b, 0, 11, 2);
    assertThat(HexFormat.of().formatHex(b)).isEqualTo("00000123456c");
    PackedDecimal.encode(new BigDecimal("-7.10"), b, 0, 11, 2);
    assertThat(HexFormat.of().formatHex(b)).isEqualTo("00000000710d");
  }

  @Test
  void decodesAndValidates() {
    byte[] b = HexFormat.of().parseHex("00000000013c");
    assertThat(PackedDecimal.decode(b, 0, 11, 0)).isEqualByComparingTo("13");
    assertThat(PackedDecimal.isNumeric(b, 0, 11)).isTrue();
    assertThat(PackedDecimal.isNumeric(HexFormat.of().parseHex("404040404040"), 0, 11)).isFalse();
    assertThat(PackedDecimal.length(11)).isEqualTo(6);
  }
}
