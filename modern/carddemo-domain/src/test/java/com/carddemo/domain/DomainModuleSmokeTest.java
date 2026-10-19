package com.carddemo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.domain")).isNotNull();
    assertThat(getClass().getClassLoader().getResource("com/carddemo/domain/package-info.class"))
        .isNotNull();
  }
}
