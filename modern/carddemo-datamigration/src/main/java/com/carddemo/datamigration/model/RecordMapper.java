package com.carddemo.datamigration.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection bridge between a contracts-v1 record (one field per copybook item) and the V1 row (one
 * column per field, same order). Values are kept with their COBOL shape: PIC X padded, numerics as
 * BigDecimal with the PIC scale, int/long for integer PICs.
 */
public final class RecordMapper<T extends Record> {

  private final Class<T> type;
  private final List<FieldColumn> fields;
  private final Constructor<T> ctor;

  public RecordMapper(Class<T> type) {
    this.type = type;
    RecordComponent[] comps = type.getRecordComponents();
    this.fields = Arrays.stream(comps).map(FieldColumn::of).toList();
    try {
      this.ctor =
          type.getDeclaredConstructor(
              Arrays.stream(comps).map(RecordComponent::getType).toArray(Class<?>[]::new));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  public Class<T> type() {
    return type;
  }

  public List<FieldColumn> fields() {
    return fields;
  }

  public FieldColumn field(String columnOrCobolName) {
    return fields.stream()
        .filter(
            f -> f.column().equals(columnOrCobolName) || f.cobolName().equals(columnOrCobolName))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException(type.getSimpleName() + ": " + columnOrCobolName));
  }

  public Object[] values(T record) {
    Object[] out = new Object[fields.size()];
    for (int i = 0; i < out.length; i++) {
      out[i] = value(record, fields.get(i));
    }
    return out;
  }

  public Object value(T record, FieldColumn f) {
    try {
      return f.component().getAccessor().invoke(record);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  public T fromValues(Object[] values) {
    Object[] args = new Object[fields.size()];
    for (int i = 0; i < args.length; i++) {
      args[i] = coerce(values[i], fields.get(i));
    }
    try {
      return ctor.newInstance(args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Builds a record from COBOL field names; missing PIC X are spaces, missing numerics zero. */
  public T fromCobolNames(Map<String, Object> byName) {
    Object[] values = new Object[fields.size()];
    for (int i = 0; i < values.length; i++) {
      FieldColumn f = fields.get(i);
      Object v = byName.get(f.cobolName());
      values[i] = v != null ? v : f.isNumeric() ? BigDecimal.ZERO : " ".repeat(f.length());
    }
    return fromValues(values);
  }

  public Map<String, Object> asMap(T record) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (FieldColumn f : fields) {
      m.put(f.column(), value(record, f));
    }
    return m;
  }

  /** Canonical text of a field value, used for hashing on both sides of the reconciliation. */
  public static String canonical(Object v) {
    if (v instanceof BigDecimal d) {
      return d.toPlainString();
    }
    return String.valueOf(v);
  }

  private static Object coerce(Object v, FieldColumn f) {
    Class<?> t = f.javaType();
    if (t == String.class) {
      String s = v == null ? "" : v.toString();
      return s.length() >= f.length() ? s : s + " ".repeat(f.length() - s.length());
    }
    if (v == null) {
      v = BigDecimal.ZERO;
    }
    BigDecimal d = v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    if (t == int.class || t == Integer.class) {
      return d.intValueExact();
    }
    if (t == long.class || t == Long.class) {
      return d.longValueExact();
    }
    return d.setScale(scaleOf(f.pic()));
  }

  /** {@code S9(9)V9(2) -> 2}, {@code 9(4)V99 -> 2}. */
  public static int scaleOf(String pic) {
    int v = pic.toUpperCase().indexOf('V');
    if (v < 0) {
      return 0;
    }
    String frac = pic.substring(v + 1);
    int open = frac.indexOf('(');
    if (open >= 0) {
      return Integer.parseInt(frac.substring(open + 1, frac.indexOf(')')));
    }
    return frac.length();
  }
}
