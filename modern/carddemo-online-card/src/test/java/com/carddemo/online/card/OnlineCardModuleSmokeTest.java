package com.carddemo.online.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OnlineCardModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.online.card")).isNotNull();
    assertThat(
            getClass().getClassLoader().getResource("com/carddemo/online/card/package-info.class"))
        .isNotNull();
  }
}
