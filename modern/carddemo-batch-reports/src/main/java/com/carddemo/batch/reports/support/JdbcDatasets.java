package com.carddemo.batch.reports.support;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.CoreCodecs;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unloads a V1 table into fixed-length records (the VSAM KSDS it replaces), in primary-key order.
 * Columns are the snake_case names of the contract record components ({@code custAddrLine1} →
 * {@code cust_addr_line_1}).
 */
public final class JdbcDatasets {

  private record Table(String name, String orderBy, RecordCodec<? extends Record> codec) {}

  private static final Map<String, Table> TABLES =
      Map.of(
          "TRANSACT", new Table("transaction", "tran_id", CoreCodecs.TRANSACTION),
          "CARDXREF", new Table("card_xref", "xref_card_num", CoreCodecs.CARD_XREF),
          "TRANTYPE", new Table("transaction_type", "tran_type", CoreCodecs.TRANSACTION_TYPE),
          "TRANCATG",
              new Table(
                  "transaction_category",
                  "tran_type_cd, tran_cat_cd",
                  CoreCodecs.TRANSACTION_CATEGORY),
          "ACCTDATA", new Table("account", "acct_id", CoreCodecs.ACCOUNT),
          "CUSTDATA", new Table("customer", "cust_id", CoreCodecs.CUSTOMER));

  private final JdbcTemplate jdbc;

  public JdbcDatasets(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static boolean supports(String dd) {
    return TABLES.containsKey(dd);
  }

  public List<String> unload(String dd) {
    Table t = TABLES.get(dd);
    if (t == null) {
      throw new IllegalArgumentException("No table for dataset " + dd);
    }
    List<String> out = new ArrayList<>();
    for (Map<String, Object> row :
        jdbc.queryForList("SELECT * FROM " + t.name() + " ORDER BY " + t.orderBy())) {
      out.add(encode(t.codec(), row));
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static <T extends Record> String encode(RecordCodec<T> codec, Map<String, Object> row) {
    RecordComponent[] comps = codec.recordType().getRecordComponents();
    Object[] args = new Object[comps.length];
    Class<?>[] types = new Class<?>[comps.length];
    for (int i = 0; i < comps.length; i++) {
      types[i] = comps[i].getType();
      args[i] = convert(value(row, snake(comps[i].getName())), types[i]);
    }
    try {
      T value = (T) codec.recordType().getDeclaredConstructor(types).newInstance(args);
      return new String(codec.encode(value), FixedRecords.CHARSET);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot build " + codec.recordType().getSimpleName(), e);
    }
  }

  private static Object value(Map<String, Object> row, String column) {
    for (Map.Entry<String, Object> e : row.entrySet()) {
      if (e.getKey().equalsIgnoreCase(column)) {
        return e.getValue();
      }
    }
    throw new IllegalStateException("Missing column " + column);
  }

  private static Object convert(Object v, Class<?> type) {
    if (type == String.class) {
      return v == null ? "" : v.toString();
    }
    if (type == BigDecimal.class) {
      return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
    long n = v == null ? 0L : ((Number) v).longValue();
    return type == int.class ? (Object) (int) n : (Object) n;
  }

  static String snake(String camel) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < camel.length(); i++) {
      char c = camel.charAt(i);
      boolean digitAfterLetter =
          Character.isDigit(c) && i > 0 && !Character.isDigit(camel.charAt(i - 1));
      if (Character.isUpperCase(c) || digitAfterLetter) {
        sb.append('_');
      }
      sb.append(Character.toLowerCase(c));
    }
    return sb.toString();
  }
}
