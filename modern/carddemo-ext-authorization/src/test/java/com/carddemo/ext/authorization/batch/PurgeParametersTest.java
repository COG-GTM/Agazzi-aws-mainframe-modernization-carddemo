package com.carddemo.ext.authorization.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PurgeParametersTest {

  @Test
  void parsesSysinLikeCbpaup0c() {
    assertThat(PurgeParameters.parse("00,00001,00001,Y")).isEqualTo(new PurgeParameters(0, true));
    assertThat(PurgeParameters.parse("05,00001,00001,N")).isEqualTo(new PurgeParameters(5, false));
    assertThat(PurgeParameters.parse("XX,00001,00001,N").expiryDays()).isEqualTo(5);
    assertThat(PurgeParameters.parse(null).expiryDays()).isEqualTo(5);
  }
}
