package com.carddemo.security.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditLogTest {

  @Test
  void logInjectionIsNeutralised() {
    Map<String, String> out =
        AuditLog.sanitize(Map.of("principal", "ADMIN001\r\nevent=AUTHENTICATION_SUCCESS"));
    assertThat(out.get("principal")).doesNotContain("\r", "\n").startsWith("ADMIN001__");
  }

  @Test
  void valuesAreBoundedAndNullsDropped() {
    var fields = new java.util.HashMap<String, Object>();
    fields.put("principal", "x".repeat(1000));
    fields.put("screen", null);
    Map<String, String> out = AuditLog.sanitize(fields);
    assertThat(out).containsOnlyKeys("principal");
    assertThat(out.get("principal")).hasSize(128);
  }
}
