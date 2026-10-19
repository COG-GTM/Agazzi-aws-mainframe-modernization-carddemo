package com.carddemo.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.MaintenanceModePort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MaintenanceEndpointTest {

  static final class Files implements MaintenanceModePort {
    final Set<String> closed = new HashSet<>();

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

  @Test
  void closeAndReopenFiles() {
    Files files = new Files();
    MaintenanceEndpoint endpoint = new MaintenanceEndpoint(files);
    assertThat(endpoint.state()).containsEntry("closedFiles", List.of());

    assertThat(endpoint.closedFiles("CARDDAT, acctdat"))
        .containsEntry("closedFiles", List.of("ACCTDAT", "CARDDAT"))
        .containsEntry("inMaintenance", true);
    assertThat(endpoint.closedFiles("ACCTDAT")).containsEntry("closedFiles", List.of("ACCTDAT"));
    assertThat(endpoint.closedFiles(""))
        .containsEntry("closedFiles", List.of())
        .containsEntry("inMaintenance", false);
    assertThat(endpoint.closedFiles(null)).containsEntry("closedFiles", List.of());
    assertThat(files.closed).isEmpty();
  }
}
