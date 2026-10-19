package com.carddemo.batch.datalifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BatchDatalifecycleModuleSmokeTest {

  @Test
  void basePackageIsOnClasspath() {
    assertThat(Package.getPackage("com.carddemo.batch.datalifecycle")).isNotNull();
    assertThat(
            getClass()
                .getClassLoader()
                .getResource("com/carddemo/batch/datalifecycle/package-info.class"))
        .isNotNull();
  }
}
