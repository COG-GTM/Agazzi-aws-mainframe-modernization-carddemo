package com.carddemo.copybook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CopybookModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.copybook")).isNotNull();
    assertThat(getClass().getClassLoader().getResource("com/carddemo/copybook/package-info.class"))
        .isNotNull();
  }
}
