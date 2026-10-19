package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExtMqinquiryModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.ext.mqinquiry")).isNotNull();
    assertThat(
            getClass()
                .getClassLoader()
                .getResource("com/carddemo/ext/mqinquiry/package-info.class"))
        .isNotNull();
  }
}
