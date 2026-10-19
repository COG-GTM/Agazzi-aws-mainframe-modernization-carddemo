package com.carddemo.parity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParityModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.parity")).isNotNull();
    assertThat(getClass().getClassLoader().getResource("com/carddemo/parity/package-info.class"))
        .isNotNull();
  }
}
