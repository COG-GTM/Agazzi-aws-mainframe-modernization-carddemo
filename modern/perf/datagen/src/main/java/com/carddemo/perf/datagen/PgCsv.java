package com.carddemo.perf.datagen;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;

/** RFC 4180 CSV rows for {@code COPY ... (FORMAT csv, HEADER)}; PIC X values keep their padding. */
final class PgCsv {

  private PgCsv() {}

  static void row(Writer w, Object... values) throws IOException {
    StringBuilder sb = new StringBuilder(256);
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      Object v = values[i];
      if (v instanceof String s) {
        sb.append('"').append(s.replace("\"", "\"\"")).append('"');
      } else if (v instanceof BigDecimal d) {
        sb.append(d.toPlainString());
      } else {
        sb.append(v);
      }
    }
    w.write(sb.append('\n').toString());
  }
}
