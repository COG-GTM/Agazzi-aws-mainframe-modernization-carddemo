package com.carddemo.batch.reports.support;

import com.carddemo.contracts.port.RecordCodec;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test helper: V1-shaped tables in H2 loaded from fixed-length records. */
public final class H2Tables {

  private H2Tables() {}

  public static <T extends Record> void load(
      JdbcTemplate jdbc, String table, RecordCodec<T> codec, List<String> records)
      throws ReflectiveOperationException {
    RecordComponent[] comps = codec.recordType().getRecordComponents();
    List<String> cols = new ArrayList<>();
    for (RecordComponent c : comps) {
      String type =
          c.getType() == String.class
              ? "VARCHAR(500)"
              : c.getType() == BigDecimal.class ? "DECIMAL(13,2)" : "BIGINT";
      cols.add(JdbcDatasets.snake(c.getName()) + " " + type);
    }
    jdbc.execute("DROP TABLE IF EXISTS " + table);
    jdbc.execute("CREATE TABLE " + table + " (" + String.join(", ", cols) + ")");
    String sql =
        "INSERT INTO "
            + table
            + " VALUES ("
            + String.join(", ", java.util.Collections.nCopies(comps.length, "?"))
            + ")";
    for (String r : records) {
      T v = codec.decodeLine(r);
      Object[] args = new Object[comps.length];
      for (int i = 0; i < comps.length; i++) {
        args[i] = comps[i].getAccessor().invoke(v);
      }
      jdbc.update(sql, args);
    }
  }
}
