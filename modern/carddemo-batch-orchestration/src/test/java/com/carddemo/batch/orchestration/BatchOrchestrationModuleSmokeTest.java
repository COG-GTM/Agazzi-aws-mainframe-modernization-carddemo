package com.carddemo.batch.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BatchOrchestrationModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.batch.orchestration")).isNotNull();
    assertThat(
            getClass()
                .getClassLoader()
                .getResource("com/carddemo/batch/orchestration/package-info.class"))
        .isNotNull();
  }
}
