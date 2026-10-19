package com.carddemo.security.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Receives Boot's AuthenticationAuditListener/AuthorizationAuditListener events and writes them to
 * {@link AuditLog}. Storage is the log pipeline (CloudWatch in AWS), so {@link #find} is empty and
 * the {@code auditevents} actuator endpoint stays unexposed. Exception messages and session ids are
 * deliberately dropped.
 */
public class StructuredAuditEventRepository implements AuditEventRepository {

  private final AuditLog log;

  public StructuredAuditEventRepository(AuditLog log) {
    this.log = log;
  }

  @Override
  public void add(AuditEvent event) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("principal", event.getPrincipal());
    fields.put("outcome", event.getType().endsWith("_SUCCESS") ? "SUCCESS" : "DENIED");
    fields.put("eventTime", event.getTimestamp());
    Object details = event.getData().get("details");
    if (details instanceof WebAuthenticationDetails web) {
      fields.put("remoteAddress", web.getRemoteAddress());
    }
    Object type = event.getData().get("type");
    if (type != null) {
      String t = type.toString();
      fields.put("reason", t.substring(t.lastIndexOf('.') + 1));
    }
    log.record(event.getType(), fields);
  }

  @Override
  public List<AuditEvent> find(String principal, Instant after, String type) {
    return List.of();
  }
}
