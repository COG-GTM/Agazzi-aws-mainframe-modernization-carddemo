package com.carddemo.batch.interest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BatchInterestModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.batch.interest")).isNotNull();
    assertThat(
            getClass()
                .getClassLoader()
                .getResource("com/carddemo/batch/interest/package-info.class"))
        .isNotNull();
  }
}
