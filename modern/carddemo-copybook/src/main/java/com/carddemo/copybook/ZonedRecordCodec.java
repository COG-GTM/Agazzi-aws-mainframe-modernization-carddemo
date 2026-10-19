package com.carddemo.copybook;

import com.carddemo.contracts.copybook.CobolField;
import com.carddemo.contracts.copybook.CobolRecord;
import com.carddemo.contracts.port.RecordCodec;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fase 0 reference codec: DISPLAY-only records (PIC X and zoned 9/S9 with overpunch), driven by the
 * {@link CobolRecord}/{@link CobolField} annotations of the contracts DTOs. Bytes not covered by
 * any field are spaces. 1A replaces it with the generated codecs.
 *
 * @param <T> record DTO type
 */
public final class ZonedRecordCodec<T extends Record> implements RecordCodec<T> {

  /** Parsed layout of one field. */
  public record FieldLayout(
      String name,
      String component,
      Class<?> type,
      int offset,
      int length,
      boolean numeric,
      boolean signed,
      int scale) {}

  private final Class<T> type;
  private final int length;
  private final List<FieldLayout> fields;
  private final Constructor<T> constructor;
  private final RecordComponent[] components;
  private final Charset charset;

  private ZonedRecordCodec(Class<T> type, Charset charset) {
    CobolRecord rec = type.getAnnotation(CobolRecord.class);
    if (rec == null) {
      throw new IllegalArgumentException(type + " is not annotated with @CobolRecord");
    }
    this.type = type;
    this.length = rec.length();
    this.charset = charset;
    this.components = type.getRecordComponents();
    List<FieldLayout> list = new ArrayList<>();
    for (RecordComponent c : components) {
      CobolField f = c.getAnnotation(CobolField.class);
      if (f == null) {
        throw new IllegalArgumentException(c + " has no @CobolField");
      }
      list.add(layout(f, c));
    }
    this.fields = List.copyOf(list);
    try {
      this.constructor =
          type.getDeclaredConstructor(
              Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  public static <T extends Record> ZonedRecordCodec<T> of(Class<T> type) {
    return new ZonedRecordCodec<>(type, StandardCharsets.US_ASCII);
  }

  /** Same layout over EBCDIC CP037 bytes (zoned overpunch maps to the same characters). */
  public static <T extends Record> ZonedRecordCodec<T> ebcdic(Class<T> type) {
    return new ZonedRecordCodec<>(type, Charset.forName("Cp037"));
  }

  private static FieldLayout layout(CobolField f, RecordComponent c) {
    String pic = f.pic().toUpperCase(java.util.Locale.ROOT);
    boolean numeric = !pic.startsWith("X");
    boolean signed = pic.startsWith("S");
    int scale = 0;
    int v = pic.indexOf('V');
    if (v >= 0) {
      String frac = pic.substring(v + 1);
      int open = frac.indexOf('(');
      scale =
          open >= 0 ? Integer.parseInt(frac.substring(open + 1, frac.indexOf(')'))) : frac.length();
    }
    return new FieldLayout(
        f.name(), c.getName(), c.getType(), f.offset(), f.length(), numeric, signed, scale);
  }

  public List<FieldLayout> fields() {
    return fields;
  }

  @Override
  public int recordLength() {
    return length;
  }

  @Override
  public Class<T> recordType() {
    return type;
  }

  @Override
  public Charset charset() {
    return charset;
  }

  @Override
  public T decode(byte[] record) {
    if (record.length != length) {
      throw new IllegalArgumentException(
          type.getSimpleName() + ": expected " + length + " bytes, got " + record.length);
    }
    String text = new String(record, charset);
    Object[] args = new Object[fields.size()];
    for (int i = 0; i < args.length; i++) {
      FieldLayout f = fields.get(i);
      String raw = text.substring(f.offset(), f.offset() + f.length());
      args[i] = f.numeric() ? toJava(ZonedDecimal.decode(raw, f.scale(), f.signed()), f) : raw;
    }
    try {
      return constructor.newInstance(args);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public byte[] encode(T value) {
    char[] out = new char[length];
    Arrays.fill(out, ' ');
    for (int i = 0; i < fields.size(); i++) {
      FieldLayout f = fields.get(i);
      Object v;
      try {
        v = components[i].getAccessor().invoke(value);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new IllegalStateException(e);
      }
      String s;
      if (f.numeric()) {
        s = ZonedDecimal.encode(toBigDecimal(v), f.length(), f.scale(), f.signed());
      } else {
        String str = v == null ? "" : (String) v;
        s = str.length() >= f.length() ? str.substring(0, f.length()) : padRight(str, f.length());
      }
      s.getChars(0, f.length(), out, f.offset());
    }
    return new String(out).getBytes(charset);
  }

  private static String padRight(String s, int len) {
    return s + " ".repeat(len - s.length());
  }

  private static Object toJava(BigDecimal d, FieldLayout f) {
    if (f.type() == BigDecimal.class) {
      return d;
    }
    if (f.type() == int.class || f.type() == Integer.class) {
      return d.intValueExact();
    }
    if (f.type() == long.class || f.type() == Long.class) {
      return d.longValueExact();
    }
    throw new IllegalStateException("Unsupported numeric type " + f.type());
  }

  private static BigDecimal toBigDecimal(Object v) {
    if (v == null) {
      return BigDecimal.ZERO;
    }
    if (v instanceof BigDecimal b) {
      return b;
    }
    return BigDecimal.valueOf(((Number) v).longValue());
  }
}
