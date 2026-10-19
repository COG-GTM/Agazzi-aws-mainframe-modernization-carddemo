package com.carddemo.online.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScreenTextTest {

  @Test
  void spacesOrLowValues() {
    assertThat(ScreenText.isBlank(null)).isTrue();
    assertThat(ScreenText.isBlank("")).isTrue();
    assertThat(ScreenText.isBlank("   ")).isTrue();
    assertThat(ScreenText.isBlank("\u0000\u0000")).isTrue();
    assertThat(ScreenText.isBlank("  \u0000")).as("mixed is not = SPACES / LOW-VALUES").isFalse();
    assertThat(ScreenText.isBlank(" a")).isFalse();
  }

  @Test
  void picXAndErrmsg() {
    assertThat(ScreenText.picX("ABC", 5)).isEqualTo("ABC  ");
    assertThat(ScreenText.picX("ABCDEFGHIJ", 8)).isEqualTo("ABCDEFGH");
    assertThat(ScreenText.picX(null, 2)).isEqualTo("  ");
    assertThat(ScreenText.out(null)).isNull();
    assertThat(ScreenText.errmsg(null)).isEmpty();
    assertThat(ScreenText.errmsg("x".repeat(80))).hasSize(78);
  }
}
