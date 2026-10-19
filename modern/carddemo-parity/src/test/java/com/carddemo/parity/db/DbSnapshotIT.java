package com.carddemo.parity.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** DB snapshot comparator on the real contracts-v1 schema (Flyway V1-V3 + seed V2). */
@Testcontainers
class DbSnapshotIT {

  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void detectsUpdatesInsertsAndDeletesByPrimaryKey() throws Exception {
    Flyway.configure()
        .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
        .locations("classpath:db/migration", "classpath:db/seed")
        .load()
        .migrate();
    try (Connection c =
        DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
      List<String> tables = List.of("account", "transaction_type");
      DbSnapshot before = DbSnapshot.take(c, tables);
      assertThat(before.rowCount("account")).isEqualTo(50);
      assertThat(before.diff(DbSnapshot.take(c, tables))).isEmpty();
      try (Statement s = c.createStatement()) {
        s.executeUpdate(
            "UPDATE account SET acct_curr_bal = acct_curr_bal + 1, version = version + 1 WHERE acct_id = (SELECT min(acct_id) FROM account)");
        s.executeUpdate("DELETE FROM transaction_type WHERE tran_type = '07'");
      }
      List<DbSnapshot.RowChange> delta = before.diff(DbSnapshot.take(c, tables));
      assertThat(delta)
          .extracting(DbSnapshot.RowChange::kind)
          .containsExactlyInAnyOrder(
              DbSnapshot.RowChange.Kind.UPDATE, DbSnapshot.RowChange.Kind.DELETE);
      assertThat(
              delta.stream()
                  .filter(d -> d.kind() == DbSnapshot.RowChange.Kind.UPDATE)
                  .findFirst()
                  .orElseThrow()
                  .columns())
          .containsOnlyKeys("acct_curr_bal");
    }
  }
}
