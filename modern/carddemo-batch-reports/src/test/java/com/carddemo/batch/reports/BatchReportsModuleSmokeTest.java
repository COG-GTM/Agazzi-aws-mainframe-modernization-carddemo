package com.carddemo.batch.reports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BatchReportsModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.batch.reports")).isNotNull();
    assertThat(
            getClass()
                .getClassLoader()
                .getResource("com/carddemo/batch/reports/package-info.class"))
        .isNotNull();
  }
}
