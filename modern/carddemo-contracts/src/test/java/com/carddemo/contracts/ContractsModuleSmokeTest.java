package com.carddemo.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractsModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.contracts")).isNotNull();
    assertThat(getClass().getClassLoader().getResource("com/carddemo/contracts/package-info.class"))
        .isNotNull();
  }
}
