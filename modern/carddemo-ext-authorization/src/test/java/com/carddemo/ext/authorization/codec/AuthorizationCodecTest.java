package com.carddemo.ext.authorization.codec;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.ext.authorization.X1Fixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AuthorizationCodecTest {

  @Test
  void parsesCcpaurqyCsvPaddingPicX() {
    AuthorizationRequest rq =
        AuthorizationRequest.parse(X1Fixtures.request("4111111111111111", "123.456", "T1"));
    assertThat(rq.cardNum()).isEqualTo("4111111111111111");
    assertThat(rq.transactionAmt()).isEqualByComparingTo("123.45");
    assertThat(rq.transactionAmt().scale()).isEqualTo(2);
    assertThat(rq.transactionId()).isEqualTo("T1" + " ".repeat(13));
    assertThat(rq.merchantName()).hasSize(22);
    assertThat(rq.posEntryMode()).isEqualTo(5);
    assertThat(rq.authDate()).isEqualTo("220718");
  }

  @Test
  void numvHandlesSignsAndCredit() {
    assertThat(AuthorizationRequest.numval(" -12.50 ")).isEqualByComparingTo("-12.50");
    assertThat(AuthorizationRequest.numval("7.00CR")).isEqualByComparingTo("-7.00");
    assertThat(AuthorizationRequest.pad("AB", 4)).isEqualTo("AB  ");
  }

  @Test
  void formatsCcpaurlyWithTrailingComma() {
    AuthorizationReply r =
        new AuthorizationReply(
            "4111111111111111", "T1", "123456", "00", "0000", new BigDecimal("100.00"));
    assertThat(r.approved()).isTrue();
    assertThat(r.toPayload())
        .isEqualTo("4111111111111111,T1             ,123456,00,0000,        100.00,");
    assertThat(AuthorizationReply.editAmount(new BigDecimal("-5.5"))).hasSize(14).endsWith("5.50");
    assertThat(AuthorizationReply.editAmount(new BigDecimal("-5.5")).trim()).startsWith("-");
  }
}
