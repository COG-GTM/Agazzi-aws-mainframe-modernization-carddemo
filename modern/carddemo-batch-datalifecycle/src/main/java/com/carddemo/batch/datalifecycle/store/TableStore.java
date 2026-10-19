package com.carddemo.batch.datalifecycle.store;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC access to the V1 tables that replace the KSDS clusters (IDCAMS DELETE/DEFINE/REPRO). */
public class TableStore {

  private final JdbcTemplate jdbc;

  public TableStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** IDCAMS DELETE + DEFINE CLUSTER: an empty cluster. Returns the rows removed. */
  public int clear(KsdsTable table) {
    return jdbc.update("DELETE FROM " + table.table());
  }

  public long count(KsdsTable table) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table.table(), Long.class);
    return n == null ? 0 : n;
  }

  /** REPRO INFILE(PS) OUTFILE(KSDS): decodes each fixed record and inserts it. */
  public int load(KsdsTable table, List<byte[]> records) {
    return load(table, records, UnaryOperator.identity());
  }

  /** As {@link #load(KsdsTable, List)}, transforming each decoded record before insertion. */
  public int load(KsdsTable table, List<byte[]> records, UnaryOperator<Record> transform) {
    List<String> cols = table.columns();
    String sql =
        "INSERT INTO %s (%s) VALUES (%s)"
            .formatted(
                table.table(),
                String.join(", ", cols),
                String.join(", ", cols.stream().map(c -> "?").toList()));
    List<Object[]> args = new ArrayList<>();
    for (byte[] r : records) {
      Record rec = transform.apply(table.codec().decode(r));
      args.add(values(rec));
    }
    int[][] counts =
        jdbc.batchUpdate(
            sql,
            args,
            500,
            (ps, row) -> {
              for (int i = 0; i < row.length; i++) {
                ps.setObject(i + 1, row[i]);
              }
            });
    int total = 0;
    for (int[] batch : counts) {
      total += batch.length;
    }
    return total;
  }

  /** REPRO INFILE(KSDS) OUTFILE(PS): all rows in key order, re-encoded as fixed records. */
  public List<byte[]> unload(KsdsTable table) {
    Class<?> type = table.recordType();
    RecordComponent[] comps = type.getRecordComponents();
    return jdbc.query(
        "SELECT %s FROM %s ORDER BY %s"
            .formatted(String.join(", ", table.columns()), table.table(), table.orderBy()),
        (rs, n) -> {
          Object[] args = new Object[comps.length];
          Class<?>[] types = new Class<?>[comps.length];
          for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            args[i] =
                switch (types[i].getName()) {
                  case "long" -> rs.getLong(i + 1);
                  case "int" -> rs.getInt(i + 1);
                  case "java.math.BigDecimal" -> rs.getBigDecimal(i + 1);
                  default -> rs.getString(i + 1);
                };
          }
          return table.codec().encode(newRecord(type, types, args));
        });
  }

  /** Empties a non-keyed cluster table (ESDS {@code usrsec_esds}, RRDS {@code usrsec_rrds}). */
  public int clear(String table) {
    return jdbc.update("DELETE FROM " + table);
  }

  public long count(String table) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    return n == null ? 0 : n;
  }

  /**
   * REPRO of CSUSR01Y records into the ESDS or RRDS demo table; {@code keyColumn} receives the RBA
   * (ESDS, {@code n * 80}) or the relative record number (RRDS, {@code n + 1}).
   */
  public int loadUserSecurity(
      String table,
      String keyColumn,
      List<byte[]> records,
      java.util.function.IntToLongFunction key) {
    List<Object[]> args = new ArrayList<>();
    for (int i = 0; i < records.size(); i++) {
      Object[] v = values(KsdsTable.USRSEC.codec().decode(records.get(i)));
      Object[] row = new Object[v.length + 1];
      row[0] = key.applyAsLong(i);
      System.arraycopy(v, 0, row, 1, v.length);
      args.add(row);
    }
    List<String> cols = new ArrayList<>(List.of(keyColumn));
    cols.addAll(KsdsTable.USRSEC.columns());
    String sql =
        "INSERT INTO %s (%s) VALUES (%s)"
            .formatted(
                table,
                String.join(", ", cols),
                String.join(", ", cols.stream().map(c -> "?").toList()));
    jdbc.batchUpdate(sql, args);
    return args.size();
  }

  static Object[] values(Record rec) {
    RecordComponent[] comps = rec.getClass().getRecordComponents();
    Object[] v = new Object[comps.length];
    for (int i = 0; i < comps.length; i++) {
      try {
        v[i] = comps[i].getAccessor().invoke(rec);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new IllegalStateException(e);
      }
      if (v[i] instanceof BigDecimal b) {
        v[i] = b;
      }
    }
    return v;
  }

  @SuppressWarnings("unchecked")
  static <T extends Record> T newRecord(Class<?> type, Class<?>[] types, Object[] args) {
    try {
      return (T) type.getDeclaredConstructor(types).newInstance(args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
