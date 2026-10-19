package com.carddemo.copybook.codec;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.layout.LayoutNode;
import com.carddemo.copybook.layout.RecordLayout;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Codec for a {@code REDEFINES} family mapped to a {@code sealed interface}: the variant is chosen
 * from a discriminator (a field of the base layout, e.g. {@code EXPORT-REC-TYPE}, or the record
 * length for VB files) and decoded with the variant's own {@link LayoutCodec}.
 *
 * @param <T> the sealed interface
 */
public final class VariantCodec<T> implements RecordCodec<T> {

  private final Class<T> type;
  private final int length;
  private final CodecOptions options;
  private final Function<byte[], String> discriminator;
  private final Map<String, LayoutCodec<? extends T>> byKey;
  private final Map<Class<?>, LayoutCodec<? extends T>> byClass;
  private final LayoutCodec<? extends T> fallback;

  private VariantCodec(Builder<T> b) {
    this.type = b.type;
    this.length = b.length;
    this.options = b.options;
    this.discriminator = b.discriminator;
    this.byKey = Map.copyOf(b.byKey);
    this.byClass = Map.copyOf(b.byClass);
    this.fallback = b.fallback;
  }

  public static <T> Builder<T> builder(Class<T> type, int maxLength, CodecOptions options) {
    return new Builder<>(type, maxLength, options);
  }

  /** Builder. */
  public static final class Builder<T> {
    private final Class<T> type;
    private final int length;
    private final CodecOptions options;
    private Function<byte[], String> discriminator;
    private final Map<String, LayoutCodec<? extends T>> byKey = new LinkedHashMap<>();
    private final Map<Class<?>, LayoutCodec<? extends T>> byClass = new LinkedHashMap<>();
    private LayoutCodec<? extends T> fallback;

    private Builder(Class<T> type, int length, CodecOptions options) {
      this.type = type;
      this.length = length;
      this.options = options;
    }

    /** Discriminates on the (alphanumeric) value of {@code field} in {@code base}. */
    public Builder<T> discriminator(RecordLayout base, String field) {
      LayoutNode n =
          base.find(field).orElseThrow(() -> new IllegalArgumentException(field + " not found"));
      this.discriminator = r -> new String(r, n.offset(), n.length(), options.charset());
      return this;
    }

    /** Discriminates on the record length (variable-block files with one layout per length). */
    public Builder<T> byLength() {
      this.discriminator = r -> Integer.toString(r.length);
      return this;
    }

    public <V extends T> Builder<T> variant(String key, Class<V> variant, RecordLayout layout) {
      LayoutCodec<V> c = LayoutCodec.of(layout, variant, options);
      byKey.put(key, c);
      byClass.put(variant, c);
      return this;
    }

    /** Variant used when no key matches (e.g. the base layout with the raw redefined area). */
    public <V extends T> Builder<T> fallback(Class<V> variant, RecordLayout layout) {
      LayoutCodec<V> c = LayoutCodec.of(layout, variant, options);
      fallback = c;
      byClass.put(variant, c);
      return this;
    }

    public VariantCodec<T> build() {
      if (discriminator == null) {
        throw new IllegalStateException("discriminator not set");
      }
      return new VariantCodec<>(this);
    }
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
    return options.charset();
  }

  @Override
  public T decode(byte[] record) {
    LayoutCodec<? extends T> c = byKey.getOrDefault(discriminator.apply(record), fallback);
    if (c == null) {
      throw new IllegalArgumentException(
          type.getSimpleName() + ": no variant for '" + discriminator.apply(record) + "'");
    }
    return c.decode(record);
  }

  @Override
  @SuppressWarnings("unchecked")
  public byte[] encode(T value) {
    LayoutCodec<T> c = (LayoutCodec<T>) byClass.get(value.getClass());
    if (c == null) {
      throw new IllegalArgumentException("Unknown variant " + value.getClass());
    }
    return c.encode(value);
  }
}
