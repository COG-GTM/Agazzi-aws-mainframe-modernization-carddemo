package com.carddemo.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlatformModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.platform")).isNotNull();
    assertThat(getClass().getClassLoader().getResource("com/carddemo/platform/package-info.class"))
        .isNotNull();
  }
}
