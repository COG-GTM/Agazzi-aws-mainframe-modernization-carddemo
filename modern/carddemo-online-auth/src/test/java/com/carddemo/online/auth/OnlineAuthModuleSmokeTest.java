package com.carddemo.online.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OnlineAuthModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.online.auth")).isNotNull();
    assertThat(
            getClass().getClassLoader().getResource("com/carddemo/online/auth/package-info.class"))
        .isNotNull();
  }
}
