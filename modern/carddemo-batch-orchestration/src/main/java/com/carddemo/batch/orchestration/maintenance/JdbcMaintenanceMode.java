package com.carddemo.batch.orchestration.maintenance;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.MaintenanceModePort;
import java.sql.Timestamp;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared CEMT file state ({@code cics_file_status}, V600): every online node sees the batch window,
 * unlike the Fase 0 in-memory fallback.
 */
public class JdbcMaintenanceMode implements MaintenanceModePort {

  static final String OPEN = "OPEN";
  static final String CLOSED = "CLOSED";

  private final JdbcTemplate jdbc;
  private final BusinessClock clock;

  public JdbcMaintenanceMode(JdbcTemplate jdbc, BusinessClock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void close(Set<String> files) {
    files.forEach(f -> set(f, CLOSED));
  }

  @Override
  @Transactional
  public void open(Set<String> files) {
    files.forEach(f -> set(f, OPEN));
  }

  @Override
  public Set<String> closedFiles() {
    return new TreeSet<>(
        jdbc.queryForList(
            "SELECT file_name FROM cics_file_status WHERE status = ?", String.class, CLOSED));
  }

  private void set(String file, String status) {
    Timestamp now = Timestamp.valueOf(clock.now());
    int updated =
        jdbc.update(
            "UPDATE cics_file_status SET status = ?, changed_at = ?, version = version + 1"
                + " WHERE file_name = ?",
            status,
            now,
            file);
    if (updated == 0) {
      try {
        jdbc.update(
            "INSERT INTO cics_file_status (file_name, status, changed_at) VALUES (?, ?, ?)",
            file,
            status,
            now);
      } catch (DuplicateKeyException race) {
        jdbc.update(
            "UPDATE cics_file_status SET status = ?, changed_at = ?, version = version + 1"
                + " WHERE file_name = ?",
            status,
            now,
            file);
      }
    }
  }
}
