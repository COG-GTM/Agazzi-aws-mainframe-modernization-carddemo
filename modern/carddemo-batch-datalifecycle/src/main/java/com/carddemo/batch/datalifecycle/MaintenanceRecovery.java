package com.carddemo.batch.datalifecycle;

import com.carddemo.contracts.port.MaintenanceModePort;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Rebuilds the CICS maintenance window at startup from the persisted Spring Batch metadata: a
 * refresh job whose last instance ran {@code CLCIFIL} but has not completed keeps its files closed
 * until a restart reaches {@code OPCIFIL}, even when the maintenance port itself is not durable.
 */
public final class MaintenanceRecovery implements SmartInitializingSingleton {

  static final Map<String, List<String>> REFRESH_FILES = new LinkedHashMap<>();

  static {
    REFRESH_FILES.put("CARDFILE", List.of("CARDDAT", "CARDAIX"));
    REFRESH_FILES.put("CUSTFILE", List.of("CUSTDAT"));
    REFRESH_FILES.put("TRANFILE", List.of("TRANSACT", "CXACAIX"));
  }

  private final JobExplorer explorer;
  private final MaintenanceModePort maintenance;

  public MaintenanceRecovery(JobExplorer explorer, MaintenanceModePort maintenance) {
    this.explorer = explorer;
    this.maintenance = maintenance;
  }

  @Override
  public void afterSingletonsInstantiated() {
    recover();
  }

  public Set<String> recover() {
    Set<String> closed = new LinkedHashSet<>();
    REFRESH_FILES.forEach(
        (job, files) -> {
          if (interruptedAfterClose(job)) {
            closed.addAll(files);
          }
        });
    if (!closed.isEmpty()) {
      maintenance.close(closed);
    }
    return closed;
  }

  private boolean interruptedAfterClose(String job) {
    JobInstance last = explorer.getLastJobInstance(job);
    if (last == null) {
      return false;
    }
    JobExecution exec = explorer.getLastJobExecution(last);
    if (exec == null
        || exec.getStatus() == BatchStatus.COMPLETED
        || exec.getStatus() == BatchStatus.ABANDONED) {
      return false;
    }
    String clcifil = job + ".CLCIFIL";
    return explorer.getJobExecutions(last).stream()
        .flatMap(e -> e.getStepExecutions().stream())
        .anyMatch(s -> s.getStepName().equals(clcifil) && s.getStatus() == BatchStatus.COMPLETED);
  }
}
