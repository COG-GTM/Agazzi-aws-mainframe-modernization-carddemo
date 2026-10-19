package com.carddemo.security.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;

/** Security events Boot does not audit: logout (PF3 in COSGN00C/COMEN01C) and session rotation. */
public class SecurityEventAuditListener {

  private final AuditLog log;

  public SecurityEventAuditListener(AuditLog log) {
    this.log = log;
  }

  @EventListener
  public void onLogout(LogoutSuccessEvent e) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("principal", e.getAuthentication().getName());
    f.put("outcome", "SUCCESS");
    if (e.getAuthentication().getDetails() instanceof WebAuthenticationDetails web) {
      f.put("remoteAddress", web.getRemoteAddress());
    }
    log.record("LOGOUT", f);
  }

  @EventListener
  public void onSessionFixation(SessionFixationProtectionEvent e) {
    log.record(
        "SESSION_ROTATED",
        Map.of("principal", e.getAuthentication().getName(), "outcome", "SUCCESS"));
  }
}
