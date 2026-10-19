package com.carddemo.online.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OptionInputTest {

  @ParameterizedTest(name = "[{0}] -> {1} / {2}")
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '"',
      value = {
        "\"  \"|00|0",
        "\"1 \"|01|1",
        "\" 1\"|01|1",
        "01|01|1",
        "11|11|11",
        "99|99|99",
        "\"a \"|0a|-1",
        "1a|1a|-1",
        "-1|-1|-1",
        "\"\"|00|0",
        "123|12|12"
      })
  void parsesLikeProcessEnterKey(String in, String optionX, int value) {
    OptionInput o = OptionInput.parse(in);
    assertThat(o.optionX()).isEqualTo(optionX);
    assertThat(o.value()).isEqualTo(value);
    assertThat(o.numeric()).isEqualTo(value >= 0);
  }

  @ParameterizedTest
  @CsvSource({"00,false", "01,true", "11,true", "12,false"})
  void validForCount(String in, boolean valid) {
    assertThat(OptionInput.parse(in).validFor(11)).isEqualTo(valid);
  }

  @org.junit.jupiter.api.Test
  void nullIsSpacesAndLowValuesAreNotNumeric() {
    assertThat(OptionInput.parse(null).value()).isZero();
    assertThat(OptionInput.parse("\u0000\u0000").numeric()).isFalse();
    assertThat(OptionInput.parse("7").optiono()).isEqualTo("07");
  }
}
