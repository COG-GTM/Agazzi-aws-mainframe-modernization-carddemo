package com.carddemo.datamigration.store;

import com.carddemo.datamigration.io.PhysicalLayout;
import com.carddemo.datamigration.io.SourceDataset;
import com.carddemo.datamigration.io.SourceRecord;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.FieldColumn;
import com.carddemo.datamigration.model.Organization;
import com.carddemo.datamigration.model.RecordMapper;
import com.carddemo.datamigration.validate.KeyLookup;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC access to the V1 tables: idempotent upsert by primary key and ordered unload. */
public class RelationalStore implements KeyLookup {

  private final JdbcTemplate jdbc;

  public RelationalStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * {@code INSERT ... ON CONFLICT (pk) DO UPDATE ... WHERE row IS DISTINCT FROM EXCLUDED}: a re-run
   * with the same extract changes nothing (version untouched).
   *
   * @return number of rows inserted or actually updated
   */
  public int upsert(Dataset ds, List<SourceRecord> records) {
    if (records.isEmpty()) {
      return 0;
    }
    RecordMapper<Record> m = ds.mapper();
    List<String> cols = new ArrayList<>();
    String surrogate = ds.surrogateColumn();
    if (surrogate != null) {
      cols.add(surrogate);
    }
    m.fields().forEach(f -> cols.add(f.column()));
    List<String> pk = ds.primaryKey();
    List<String> data = cols.stream().filter(c -> !pk.contains(c)).toList();
    String sql =
        "INSERT INTO "
            + ds.table()
            + " AS t ("
            + String.join(", ", cols)
            + ") VALUES ("
            + cols.stream().map(c -> "?").collect(Collectors.joining(", "))
            + ") ON CONFLICT ("
            + String.join(", ", pk)
            + ") DO UPDATE SET "
            + data.stream().map(c -> c + " = EXCLUDED." + c).collect(Collectors.joining(", "))
            + ", version = t.version + 1 WHERE ("
            + data.stream().map(c -> "t." + c).collect(Collectors.joining(", "))
            + ") IS DISTINCT FROM ("
            + data.stream().map(c -> "EXCLUDED." + c).collect(Collectors.joining(", "))
            + ")";
    int[][] counts =
        jdbc.batchUpdate(
            sql,
            records,
            500,
            (ps, r) -> {
              int i = 1;
              if (surrogate != null) {
                ps.setLong(i++, ds.surrogate(r.ordinal()));
              }
              for (Object v : m.values(r.value())) {
                bind(ps, i++, v);
              }
            });
    int changed = 0;
    for (int[] batch : counts) {
      for (int c : batch) {
        changed += Math.max(c, 0);
      }
    }
    return changed;
  }

  /** Re-aligns identity sequences after explicit surrogate inserts. */
  public void syncIdentity(Dataset ds) {
    if (ds.organization() == Organization.PS || ds.organization() == Organization.ESDS) {
      String col = ds.surrogateColumn();
      jdbc.queryForObject(
          "SELECT setval(pg_get_serial_sequence('"
              + ds.table()
              + "', '"
              + col
              + "'), COALESCE((SELECT MAX("
              + col
              + ") FROM "
              + ds.table()
              + "), 0) + 1, false)",
          Long.class);
    }
  }

  public void deleteAll(Dataset ds) {
    jdbc.update("DELETE FROM " + ds.table());
  }

  /** Users already migrated by usrsecToBcrypt. */
  public long hashedPasswords() {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE sec_usr_pwd LIKE '{%'", Long.class);
    return n == null ? 0 : n;
  }

  public long count(Dataset ds) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + ds.table(), Long.class);
    return n == null ? 0 : n;
  }

  public BigDecimal sum(Dataset ds, String column) {
    BigDecimal s =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(" + column + "), 0) FROM " + ds.table(), BigDecimal.class);
    return s == null ? BigDecimal.ZERO : s;
  }

  public long maxVersion(Dataset ds) {
    Long v = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) FROM " + ds.table(), Long.class);
    return v == null ? 0 : v;
  }

  /** All rows as records; ordinal = surrogate position (PS/ESDS/RRDS) or row number. */
  public List<SourceRecord> readAll(Dataset ds) {
    RecordMapper<Record> m = ds.mapper();
    String surrogate = ds.surrogateColumn();
    String sql =
        "SELECT "
            + (surrogate == null ? "" : surrogate + ", ")
            + m.fields().stream().map(FieldColumn::column).collect(Collectors.joining(", "))
            + " FROM "
            + ds.table()
            + " ORDER BY "
            + String.join(", ", ds.primaryKey());
    List<SourceRecord> out = new ArrayList<>();
    jdbc.query(
        sql,
        (ResultSet rs) -> {
          int base = surrogate == null ? 0 : 1;
          Object[] vals = new Object[m.fields().size()];
          for (int i = 0; i < vals.length; i++) {
            FieldColumn f = m.fields().get(i);
            vals[i] = f.isNumeric() ? rs.getBigDecimal(base + i + 1) : rs.getString(base + i + 1);
          }
          long ordinal =
              surrogate == null
                  ? out.size() + 1L
                  : ds.organization() == Organization.ESDS
                      ? rs.getLong(1) / ds.recordLength() + 1
                      : rs.getLong(1);
          out.add(new SourceRecord(ordinal, m.fromValues(vals)));
        });
    return out;
  }

  @Override
  public Optional<Set<String>> keys(Dataset ds, List<String> columns) {
    if (count(ds) == 0) {
      return Optional.empty();
    }
    String sql = "SELECT DISTINCT " + String.join(", ", columns) + " FROM " + ds.table();
    Set<String> out = new HashSet<>();
    jdbc.query(
        sql,
        (ResultSet rs) -> {
          StringBuilder sb = new StringBuilder();
          for (int i = 1; i <= columns.size(); i++) {
            if (i > 1) {
              sb.append('|');
            }
            Object v = rs.getObject(i);
            sb.append(v instanceof BigDecimal d ? d.toPlainString() : String.valueOf(v));
          }
          out.add(sb.toString());
        });
    return Optional.of(out);
  }

  /** Remembers how the extract was laid out so that the unload can rebuild it byte-identical. */
  public void saveLayout(SourceDataset src, long jobExecutionId, Instant loadedAt) {
    jdbc.update(
        "INSERT INTO dm_dataset_layout (dataset, source_format, source_file, layout, record_count,"
            + " source_sha256, job_execution_id, loaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT (dataset) DO UPDATE SET source_format = EXCLUDED.source_format,"
            + " source_file = EXCLUDED.source_file, layout = EXCLUDED.layout,"
            + " record_count = EXCLUDED.record_count, source_sha256 = EXCLUDED.source_sha256,"
            + " job_execution_id = EXCLUDED.job_execution_id, loaded_at = EXCLUDED.loaded_at",
        src.dataset().name(),
        src.format().name(),
        src.file(),
        src.layout().encode(),
        src.records().size(),
        src.sha256(),
        jobExecutionId,
        Timestamp.from(loadedAt));
  }

  /** Layout captured by the last ASCII import of the dataset, if any. */
  public Optional<PhysicalLayout> asciiLayout(Dataset ds) {
    List<String> l =
        jdbc.queryForList(
            "SELECT layout FROM dm_dataset_layout WHERE dataset = ? AND source_format = 'ASCII'",
            String.class,
            ds.name());
    return l.stream().findFirst().map(PhysicalLayout::decode);
  }

  public void saveReconciliation(
      long jobExecutionId, String dataset, String metric, String source, String target) {
    jdbc.update(
        "INSERT INTO dm_reconciliation (job_execution_id, dataset, metric, source_value,"
            + " target_value, matched) VALUES (?, ?, ?, ?, ?, ?)",
        jobExecutionId,
        dataset,
        metric,
        source,
        target,
        java.util.Objects.equals(source, target));
  }

  private static void bind(PreparedStatement ps, int i, Object v) throws SQLException {
    if (v instanceof BigDecimal d) {
      ps.setBigDecimal(i, d);
    } else if (v instanceof Integer n) {
      ps.setInt(i, n);
    } else if (v instanceof Long n) {
      ps.setLong(i, n);
    } else {
      ps.setString(i, (String) v);
    }
  }
}
