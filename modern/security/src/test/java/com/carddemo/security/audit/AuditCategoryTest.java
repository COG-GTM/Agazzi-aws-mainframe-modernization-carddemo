package com.carddemo.security.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class AuditCategoryTest {

  @Test
  @SuppressWarnings("unchecked")
  void everyContractPathHasAnAuditCategory() throws Exception {
    Map<String, Object> paths;
    try (InputStream in = getClass().getResourceAsStream("/openapi/openapi.yaml")) {
      assertThat(in).as("openapi.yaml from carddemo-contracts").isNotNull();
      paths = (Map<String, Object>) new Yaml().<Map<String, Object>>load(in).get("paths");
    }
    assertThat(paths).isNotEmpty();
    paths
        .keySet()
        .forEach(p -> assertThat(AuditCategory.of(p)).as(p).isNotEqualTo(AuditCategory.UNKNOWN));
  }

  @Test
  void sensitiveScreensAreCategorised() {
    assertThat(AuditCategory.of("/api/v1/screens/cosgn00")).isEqualTo(AuditCategory.SIGNON);
    assertThat(AuditCategory.of("/api/v1/screens/COUSR03")).isEqualTo(AuditCategory.ADMIN);
    assertThat(AuditCategory.of("/api/v1/screens/coactup")).isEqualTo(AuditCategory.UPDATE);
    assertThat(AuditCategory.of("/api/v1/screens/cotrn01")).isEqualTo(AuditCategory.READ);
    assertThat(AuditCategory.of("/api/v1/jobs/42")).isEqualTo(AuditCategory.JOB);
    assertThat(AuditCategory.of("/actuator/maintenance")).isEqualTo(AuditCategory.ADMIN);
    assertThat(AuditCategory.of("/api/v1/screens/../x")).isEqualTo(AuditCategory.UNKNOWN);
  }
}
