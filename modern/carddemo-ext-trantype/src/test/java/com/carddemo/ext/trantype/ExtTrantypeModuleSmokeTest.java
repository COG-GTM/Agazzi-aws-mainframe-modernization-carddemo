package com.carddemo.ext.trantype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExtTrantypeModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.ext.trantype")).isNotNull();
    assertThat(
            getClass().getClassLoader().getResource("com/carddemo/ext/trantype/package-info.class"))
        .isNotNull();
  }
}
