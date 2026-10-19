package com.carddemo.app.ops;

import com.carddemo.contracts.port.MaintenanceModePort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * {@code /actuator/maintenance}: CEMT SET FILE CLOSE/OPEN for the cutover runbook (T-0 0.9/0.11,
 * R.2). GET returns the closed CICS files; POST {@code {"closedFiles":[...]}} sets the closed set
 * (empty = OPENFIL, all files = CLOSEFIL). Write access is ADMIN-only (O1 security chain).
 */
@Component
@Endpoint(id = "maintenance")
public class MaintenanceEndpoint {

  private final MaintenanceModePort maintenance;

  public MaintenanceEndpoint(MaintenanceModePort maintenance) {
    this.maintenance = maintenance;
  }

  @ReadOperation
  public Map<String, Object> state() {
    Set<String> closed = new TreeSet<>(maintenance.closedFiles());
    return Map.of("closedFiles", List.copyOf(closed), "inMaintenance", !closed.isEmpty());
  }

  @WriteOperation
  public Map<String, Object> closedFiles(@Nullable List<String> closedFiles) {
    Set<String> target = new TreeSet<>(closedFiles == null ? List.of() : closedFiles);
    Set<String> toOpen = new TreeSet<>(maintenance.closedFiles());
    toOpen.removeAll(target);
    Set<String> toClose = new TreeSet<>(target);
    toClose.removeAll(maintenance.closedFiles());
    if (!toOpen.isEmpty()) {
      maintenance.open(toOpen);
    }
    if (!toClose.isEmpty()) {
      maintenance.close(toClose);
    }
    return state();
  }
}
