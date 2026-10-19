package com.carddemo.ext.authorization.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthKeyTest {

  @Test
  void roundTripsPaut9cts() {
    AuthKey k = new AuthKey(99999 - 22199, 999_999_999L - 120_000_000L);
    assertThat(AuthKey.fromBytes(k.toBytes(), 0)).isEqualTo(k);
    assertThat(AuthKey.fromHex(k.toHex())).isEqualTo(k);
    assertThat(k.toBytes()).hasSize(8);
  }
}
