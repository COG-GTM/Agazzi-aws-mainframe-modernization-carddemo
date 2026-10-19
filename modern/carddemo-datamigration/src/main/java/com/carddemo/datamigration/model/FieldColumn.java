package com.carddemo.datamigration.model;

import com.carddemo.contracts.copybook.CobolField;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;

/** One copybook field of a contracts-v1 record and the V1 column that stores it. */
public record FieldColumn(
    String cobolName,
    String pic,
    int offset,
    int length,
    String column,
    RecordComponent component) {

  static FieldColumn of(RecordComponent c) {
    CobolField f = c.getAnnotation(CobolField.class);
    if (f == null) {
      throw new IllegalArgumentException("missing @CobolField on " + c);
    }
    return new FieldColumn(f.name(), f.pic(), f.offset(), f.length(), snake(c.getName()), c);
  }

  public Class<?> javaType() {
    return component.getType();
  }

  public boolean isNumeric() {
    return javaType() != String.class;
  }

  public boolean isDecimal() {
    return javaType() == BigDecimal.class;
  }

  /** {@code custAddrLine1 -> cust_addr_line_1} (the V1 naming rule). */
  public static String snake(String camel) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < camel.length(); i++) {
      char ch = camel.charAt(i);
      boolean boundary =
          i > 0
              && (Character.isUpperCase(ch)
                  || (Character.isDigit(ch) && !Character.isDigit(camel.charAt(i - 1))));
      if (boundary) {
        sb.append('_');
      }
      sb.append(Character.toLowerCase(ch));
    }
    return sb.toString();
  }
}
