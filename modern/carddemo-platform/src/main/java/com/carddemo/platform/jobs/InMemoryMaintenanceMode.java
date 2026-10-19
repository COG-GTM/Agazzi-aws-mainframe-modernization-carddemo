package com.carddemo.platform.jobs;

import com.carddemo.contracts.port.MaintenanceModePort;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Fase 0 CEMT OPEN/CLOSE state, single node. B5 replaces it with a shared (DB) implementation. */
public final class InMemoryMaintenanceMode implements MaintenanceModePort {

  private final Set<String> closed = ConcurrentHashMap.newKeySet();

  @Override
  public void close(Set<String> files) {
    closed.addAll(files);
  }

  @Override
  public void open(Set<String> files) {
    closed.removeAll(files);
  }

  @Override
  public Set<String> closedFiles() {
    return Set.copyOf(closed);
  }
}
