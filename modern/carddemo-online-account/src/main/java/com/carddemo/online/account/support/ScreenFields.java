package com.carddemo.online.account.support;

import com.carddemo.contracts.screen.ScreenField;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts generated map DTOs ({@code <Map>Screen.Input/Output}) to and from BMS-name maps. */
public final class ScreenFields {

  private ScreenFields() {}

  /** BMS field name to value (nulls kept). */
  public static Map<String, String> toMap(Record dto) {
    Map<String, String> out = new LinkedHashMap<>();
    if (dto == null) {
      return out;
    }
    for (RecordComponent rc : dto.getClass().getRecordComponents()) {
      try {
        out.put(name(rc), (String) rc.getAccessor().invoke(dto));
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
    return out;
  }

  /** Builds a DTO; each value is moved to its {@code PIC X(length)} and presentation-trimmed. */
  public static <T extends Record> T fromMap(Class<T> type, Map<String, String> values) {
    RecordComponent[] rcs = type.getRecordComponents();
    Object[] args = new Object[rcs.length];
    Class<?>[] types = new Class<?>[rcs.length];
    for (int i = 0; i < rcs.length; i++) {
      types[i] = String.class;
      String v = values.get(name(rcs[i]));
      ScreenField sf = rcs[i].getAnnotation(ScreenField.class);
      args[i] = v == null ? null : CobolText.display(CobolText.pad(v, sf.length()));
    }
    try {
      return type.getDeclaredConstructor(types).newInstance(args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String name(RecordComponent rc) {
    return rc.getAccessor().getAnnotation(JsonProperty.class).value();
  }
}
