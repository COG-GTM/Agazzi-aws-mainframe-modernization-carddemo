package com.carddemo.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Table derived from the branches of {@code app/asm/COBDATFT.asm}. */
class CobolDateFormatterTest {

  @ParameterizedTest(name = "type {0} ''{1}'' -> type {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        // type 1 (YYYYMMDD) -> separated
        "1|20220718|1|2022-07-18|''",
        "1|20220718|' '|2022-07-18|''",
        "1|00000000|1|0000-00-00|''",
        "1|99991231|1|9999-12-31|''",
        // no calendar check: bytes are copied
        "1|20221399|1|2022-13-99|''",
        "1|ABCDEFGH|1|ABCD-EF-GH|''",
        "1|2022|1|2022-  -  |''",
        // type 1 rejections: '-' in position 5 or output type 2
        "1|2022-07-18|1|''|INVALID INPUT",
        "1|20220718|2|''|INVALID INPUT",
        // type 2 (YYYY-MM-DD) -> unseparated
        "2|2022-07-18|2|20220718|''",
        "2|2022-07-18|' '|20220718|''",
        "2|2024-02-29|2|20240229|''",
        // separators are not checked (the CLC is commented out in the assembler)
        "2|2022/07/18|2|20220718|''",
        "2|'20220718  '|2|202271|''",
        "2|2022-07-18|1|''|INVALID INPUT",
        // unknown input type
        "3|20220718|1|''|INVALID INPUT",
        "' '|20220718|1|''|INVALID INPUT"
      })
  void convertsLikeTheAssembler(
      char inType, String input, char outType, String expected, String error) {
    CobolDateFormatter.Result result = CobolDateFormatter.convert(inType, input, outType);

    assertThat(result.outputDate()).hasSize(20);
    assertThat(result.errorMessage()).hasSize(38);
    assertThat(result.date()).isEqualTo(expected.stripTrailing());
    assertThat(result.outputDate()).startsWith(expected);
    assertThat(result.errorMessage().stripTrailing()).isEqualTo(error);
    assertThat(result.valid()).isEqualTo(error.isEmpty());
  }

  @org.junit.jupiter.api.Test
  void readacctReissueDateUsesType2ToType2() {
    assertThat(CobolDateFormatter.convert('2', "2014-11-20", '2').date()).isEqualTo("20141120");
    assertThat(CobolDateFormatter.toUnseparated("2014-11-20").date()).isEqualTo("20141120");
    assertThat(CobolDateFormatter.toSeparated("20141120").date()).isEqualTo("2014-11-20");
  }
}
