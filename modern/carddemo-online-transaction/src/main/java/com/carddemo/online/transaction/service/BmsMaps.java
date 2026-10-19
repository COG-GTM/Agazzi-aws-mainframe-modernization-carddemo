package com.carddemo.online.transaction.service;

import com.carddemo.contracts.screen.ScreenField;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bridges the contracts-v1 map DTOs ({@code <Map>Screen.Input/Output}) and the field-name keyed
 * symbolic map used by the services (BMS names as in {@code app/cpy-bms}).
 */
public final class BmsMaps {

  private BmsMaps() {}

  /** BMS field name to length of every component of a map record. */
  public static Map<String, Integer> lengths(Class<? extends Record> type) {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (RecordComponent c : type.getRecordComponents()) {
      out.put(name(type, c), c.getAnnotation(ScreenField.class).length());
    }
    return out;
  }

  /**
   * RECEIVE MAP: every input field padded to its length (JUSTIFY=(LEFT,BLANK)); fields in {@code
   * numFields} (ATTRB=NUM) are right-justified and zero-filled. Missing fields are LOW-VALUES ("").
   */
  public static Map<String, String> receive(
      Class<? extends Record> type, Record input, Set<String> numFields) {
    Map<String, String> out = new LinkedHashMap<>();
    for (RecordComponent c : type.getRecordComponents()) {
      String name = name(type, c);
      int len = c.getAnnotation(ScreenField.class).length();
      String v = input == null ? null : (String) invoke(c, input);
      if (v == null || v.isEmpty()) {
        out.put(name, "");
      } else if (numFields.contains(name) && !CobolFmt.blank(v)) {
        String t = v.strip();
        t = t.length() > len ? t.substring(0, len) : t;
        out.put(name, "0".repeat(len - t.length()) + t);
      } else {
        out.put(name, CobolFmt.pad(v, len));
      }
    }
    return out;
  }

  /** Builds the output DTO; absent fields (LOW-VALUES) are {@code null}, others right-trimmed. */
  public static <R extends Record> R build(Class<R> type, Map<String, String> values) {
    RecordComponent[] comps = type.getRecordComponents();
    Object[] args = new Object[comps.length];
    Class<?>[] types = new Class<?>[comps.length];
    for (int i = 0; i < comps.length; i++) {
      types[i] = comps[i].getType();
      args[i] = CobolFmt.rtrim(values.get(name(type, comps[i])));
    }
    try {
      return type.getDeclaredConstructor(types).newInstance(args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot build " + type.getName(), e);
    }
  }

  private static String name(Class<?> type, RecordComponent c) {
    try {
      JsonProperty p = type.getDeclaredField(c.getName()).getAnnotation(JsonProperty.class);
      return p == null ? c.getName().toUpperCase(java.util.Locale.ROOT) : p.value();
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Object invoke(RecordComponent c, Record r) {
    try {
      return c.getAccessor().invoke(r);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
