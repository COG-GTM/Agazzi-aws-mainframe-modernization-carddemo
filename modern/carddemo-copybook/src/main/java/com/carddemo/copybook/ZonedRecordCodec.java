package com.carddemo.copybook;

import com.carddemo.contracts.copybook.CobolField;
import com.carddemo.contracts.copybook.CobolRecord;
import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.codec.CodecOptions;
import com.carddemo.copybook.codec.LayoutCodec;
import com.carddemo.copybook.layout.LayoutNode;
import com.carddemo.copybook.layout.Picture;
import com.carddemo.copybook.layout.RecordLayout;
import com.carddemo.copybook.layout.Usage;
import java.lang.reflect.RecordComponent;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Codec for the contracts DTOs annotated with {@link CobolRecord}/{@link CobolField}. Since 1A the
 * annotations are turned into a {@link RecordLayout} and encoding/decoding is delegated to the
 * layout engine ({@link LayoutCodec}); bytes not covered by any field are spaces. The public API is
 * unchanged from Fase 0.
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
  private final Charset charset;
  private final RecordLayout layout;
  private final LayoutCodec<T> delegate;

  private ZonedRecordCodec(Class<T> type, Charset charset) {
    CobolRecord rec = type.getAnnotation(CobolRecord.class);
    if (rec == null) {
      throw new IllegalArgumentException(type + " is not annotated with @CobolRecord");
    }
    this.type = type;
    this.length = rec.length();
    this.charset = charset;
    List<FieldLayout> list = new ArrayList<>();
    List<LayoutNode> nodes = new ArrayList<>();
    for (RecordComponent c : type.getRecordComponents()) {
      CobolField f = c.getAnnotation(CobolField.class);
      if (f == null) {
        throw new IllegalArgumentException(c + " has no @CobolField");
      }
      Picture p = Picture.parse(f.pic());
      list.add(
          new FieldLayout(
              f.name(),
              c.getName(),
              c.getType(),
              f.offset(),
              f.length(),
              p.category() != Picture.Category.ALPHANUMERIC,
              p.signed(),
              p.scale()));
      nodes.add(
          new LayoutNode(
              5,
              f.name(),
              p,
              Usage.DISPLAY,
              f.offset(),
              f.length(),
              1,
              1,
              null,
              null,
              List.of(),
              List.of()));
    }
    this.fields = List.copyOf(list);
    this.layout =
        new RecordLayout(
            rec.copybook(),
            new LayoutNode(
                1,
                rec.copybook(),
                null,
                Usage.DISPLAY,
                0,
                length,
                1,
                1,
                null,
                null,
                nodes,
                List.of()));
    this.delegate =
        LayoutCodec.of(layout, type, new CodecOptions(charset, ZonedDecimal.SignStyle.OVERPUNCH));
  }

  public static <T extends Record> ZonedRecordCodec<T> of(Class<T> type) {
    return new ZonedRecordCodec<>(type, StandardCharsets.US_ASCII);
  }

  /** Same layout over EBCDIC CP037 bytes (zoned overpunch maps to the same characters). */
  public static <T extends Record> ZonedRecordCodec<T> ebcdic(Class<T> type) {
    return new ZonedRecordCodec<>(type, CodecOptions.CP037);
  }

  public List<FieldLayout> fields() {
    return fields;
  }

  /** The {@link RecordLayout} derived from the annotations. */
  public RecordLayout layout() {
    return layout;
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
    return delegate.decode(record);
  }

  @Override
  public byte[] encode(T value) {
    return delegate.encode(value);
  }
}
