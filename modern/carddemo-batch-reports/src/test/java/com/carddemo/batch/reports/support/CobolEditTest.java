package com.carddemo.batch.reports.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Expected values captured from GnuCOBOL 3.1.2 ({@code -std=ibm}) MOVEs into the same PICs. */
class CobolEditTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "0|[               ]|[               ]|[         .00 ]|[000000000.00 ]",
        "-0.05|[-           .05]|[-           .05]|[         .05-]|[000000000.05-]",
        "-1234567.89|[-  1,234,567.89]|[-  1,234,567.89]|[  1234567.89-]|[001234567.89-]",
        "9876543210.12|[ 876,543,210.12]|[+876,543,210.12]|[876543210.12 ]|[876543210.12 ]",
        "0.5|[            .50]|[+           .50]|[         .50 ]|[000000000.50 ]"
      })
  void editsLikeCobol(String value, String minus, String plus, String zsup, String zfill) {
    BigDecimal v = new BigDecimal(value);
    assertThat("[" + CobolEdit.minusZzz(v) + "]").isEqualTo(minus);
    assertThat("[" + CobolEdit.plusZzz(v) + "]").isEqualTo(plus);
    assertThat("[" + CobolEdit.zeroSuppressedTrailingSign(v) + "]").isEqualTo(zsup);
    assertThat("[" + CobolEdit.zeroFilledTrailingSign(v) + "]").isEqualTo(zfill);
  }
}
