package com.carddemo.parity.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Snapshot of database tables keyed by primary key, and the delta between two snapshots (plan §11:
 * "delta DB" of batch jobs and online scenarios). The {@code version} column is ignored.
 */
public final class DbSnapshot {

  /** Row change in a table: kind, primary key, changed columns (expected = before). */
  public record RowChange(String table, Kind kind, String key, Map<String, String[]> columns) {
    /** INSERT, DELETE or UPDATE. */
    public enum Kind {
      INSERT,
      DELETE,
      UPDATE
    }
  }

  private final Map<String, Map<String, Map<String, String>>> tables;

  private DbSnapshot(Map<String, Map<String, Map<String, String>>> tables) {
    this.tables = tables;
  }

  public static DbSnapshot take(Connection c, List<String> tableNames) throws SQLException {
    Map<String, Map<String, Map<String, String>>> all = new LinkedHashMap<>();
    DatabaseMetaData md = c.getMetaData();
    for (String t : tableNames) {
      List<String> pk = new ArrayList<>();
      try (ResultSet rs = md.getPrimaryKeys(null, null, t)) {
        Map<Integer, String> seq = new TreeMap<>();
        while (rs.next()) {
          seq.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
        }
        pk.addAll(seq.values());
      }
      Map<String, Map<String, String>> rows = new TreeMap<>();
      try (Statement s = c.createStatement();
          ResultSet rs = s.executeQuery("SELECT * FROM " + t)) {
        ResultSetMetaData m = rs.getMetaData();
        while (rs.next()) {
          Map<String, String> row = new LinkedHashMap<>();
          for (int i = 1; i <= m.getColumnCount(); i++) {
            String col = m.getColumnName(i);
            if (!col.equalsIgnoreCase("version")) {
              Object v = rs.getObject(i);
              row.put(col, v == null ? null : v.toString());
            }
          }
          String key =
              pk.isEmpty() ? row.toString() : String.join("|", pk.stream().map(row::get).toList());
          rows.put(key, row);
        }
      }
      all.put(t, rows);
    }
    return new DbSnapshot(all);
  }

  public int rowCount(String table) {
    return tables.getOrDefault(table, Map.of()).size();
  }

  /** Changes from {@code this} (before) to {@code after}. */
  public List<RowChange> diff(DbSnapshot after) {
    List<RowChange> out = new ArrayList<>();
    for (String t : tables.keySet()) {
      Map<String, Map<String, String>> b = tables.get(t);
      Map<String, Map<String, String>> a = after.tables.getOrDefault(t, Map.of());
      for (var e : b.entrySet()) {
        Map<String, String> ar = a.get(e.getKey());
        if (ar == null) {
          out.add(new RowChange(t, RowChange.Kind.DELETE, e.getKey(), Map.of()));
        } else {
          Map<String, String[]> cols = new LinkedHashMap<>();
          e.getValue()
              .forEach(
                  (col, v) -> {
                    if (!Objects.equals(v, ar.get(col))) {
                      cols.put(col, new String[] {v, ar.get(col)});
                    }
                  });
          if (!cols.isEmpty()) {
            out.add(new RowChange(t, RowChange.Kind.UPDATE, e.getKey(), cols));
          }
        }
      }
      for (String k : a.keySet()) {
        if (!b.containsKey(k)) {
          out.add(new RowChange(t, RowChange.Kind.INSERT, k, Map.of()));
        }
      }
    }
    return out;
  }
}
