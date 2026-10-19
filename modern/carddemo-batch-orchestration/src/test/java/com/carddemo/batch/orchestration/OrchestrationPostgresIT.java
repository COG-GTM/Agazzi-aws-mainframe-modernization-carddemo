package com.carddemo.batch.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.orchestration.maintenance.JdbcMaintenanceMode;
import com.carddemo.contracts.port.MaintenanceModePort;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 16 + Flyway V1/V3 (domain) + V600/V601 (B5): the jobs on the real schema. */
@Testcontainers
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/migration",
      "spring.batch.jdbc.initialize-schema=never",
      "spring.sql.init.mode=never"
    })
class OrchestrationPostgresIT extends BatchTestBase {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MaintenanceModePort maintenance;
  @Autowired JdbcTemplate jdbc;

  @Test
  void flywayAppliesB5MigrationsAfterTheBaseline() {
    assertThat(
            jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class))
        .contains("1", "3", "600", "601");
  }

  @Test
  void maintenanceWindowAndFullCycleOnPostgres() throws Exception {
    assertThat(maintenance).isInstanceOf(JdbcMaintenanceMode.class);
    RunLog.FAIL_ONCE.add("COMBTRAN");
    var p = params().toJobParameters();
    JobExecution failed = run("fullBatchCycle", p);
    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(maintenance.closedFiles()).hasSize(5);
    JobExecution restarted = run("fullBatchCycle", p);
    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(maintenance.closedFiles()).isEmpty();
    assertThat(RunLog.runs())
        .containsExactly(
            "POSTTRAN", "INTCALC", "TRANBKP", "COMBTRAN", "COMBTRAN", "TRANREPT", "CREASTMT");
  }

  @Test
  void cbadmcdjLoadsTheCsdOnPostgres() throws Exception {
    assertThat(run("CBADMCDJ").getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM cics_csd_definition WHERE csd_group='CARDDEMO'",
                Integer.class))
        .isGreaterThan(30);
  }
}
