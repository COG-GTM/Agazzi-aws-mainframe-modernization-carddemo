package com.carddemo.ext.authorization.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ScreenFormatTest {

  @Test
  void bmsEdits() {
    assertThat(ScreenFormat.amount(new BigDecimal("1234.5"), 8)).isEqualTo("     1234.50");
    assertThat(ScreenFormat.amount(new BigDecimal("-12.345"), 5)).isEqualTo("-   12.34");
    assertThat(ScreenFormat.date("220718")).isEqualTo("07/18/22");
    assertThat(ScreenFormat.time("134501")).isEqualTo("13:45:01");
    assertThat(AuthorizationScreenSupport.name("Ann  ", "Marie", "Lee   ")).isEqualTo("Ann M Lee");
    assertThat(AuthorizationScreenSupport.untilDoubleSpace("1 Main St  x")).isEqualTo("1 Main St");
  }
}
