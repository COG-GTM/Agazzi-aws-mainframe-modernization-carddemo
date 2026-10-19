package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MqMessageLayoutTest {

  @Test
  void picPadsAndTruncates() {
    assertThat(MqMessageLayout.pic("AB", 4)).isEqualTo("AB  ");
    assertThat(MqMessageLayout.pic("ABCDE", 4)).isEqualTo("ABCD");
    assertThat(MqMessageLayout.pic(null, 2)).isEqualTo("  ");
    assertThat(MqMessageLayout.buffer("X")).hasSize(MqMessageLayout.MESSAGE_LENGTH);
  }

  @Test
  void nonAsciiBecomesSingleByteSubstitute() {
    assertThat(MqMessageLayout.ascii("A\u00e9\uD83D\uDE00B")).isEqualTo("A??B");
    assertThat(MqMessageLayout.buffer("\u00e9").getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .hasSize(1000);
  }

  @Test
  void unsignedDisplayKeepsLowOrderDigits() {
    assertThat(MqMessageLayout.unsigned(7, 2)).isEqualTo("07");
    assertThat(MqMessageLayout.unsigned(2080, 2)).isEqualTo("80");
    assertThat(MqMessageLayout.unsigned(-17, 5)).isEqualTo("00017");
  }

  @Test
  void errorDisplayLayoutIs113Bytes() {
    String msg = MqErrorMessage.format("", "ERROR WHILE READING ACCTFILE", 17, 0, "Q");
    assertThat(msg)
        .hasSize(113)
        .isEqualTo(" ".repeat(25) + "  ERROR WHILE READING ACCTF  17  00000  Q" + " ".repeat(47));
  }
}
