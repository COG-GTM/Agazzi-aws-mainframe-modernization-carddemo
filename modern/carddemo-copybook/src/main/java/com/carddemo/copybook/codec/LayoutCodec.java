package com.carddemo.copybook.codec;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.layout.LayoutNode;
import com.carddemo.copybook.layout.RecordLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link RecordCodec} driven by a {@link RecordLayout}: binds the storage-owning items of every
 * group positionally to the components of a Java {@code record} (nested groups to nested records,
 * {@code OCCURS} to {@code List}). Supports DISPLAY zoned, {@code COMP}, {@code COMP-5}, {@code
 * COMP-3}, fixed tables and a trailing {@code OCCURS DEPENDING ON} table (the record is then
 * variable length: {@link #encode} returns only the occupied bytes).
 *
 * @param <T> Java record type
 */
public final class LayoutCodec<T> implements RecordCodec<T> {

  private final RecordLayout layout;
  private final Class<T> type;
  private final CodecOptions options;
  private final GroupBinder root;

  private LayoutCodec(RecordLayout layout, Class<T> type, CodecOptions options) {
    this.layout = layout;
    this.type = type;
    this.options = options;
    this.root = new GroupBinder(layout.root(), type);
  }

  public static <T> LayoutCodec<T> of(RecordLayout layout, Class<T> type, CodecOptions options) {
    return new LayoutCodec<>(layout, type, options);
  }

  public RecordLayout layout() {
    return layout;
  }

  public CodecOptions options() {
    return options;
  }

  @Override
  public int recordLength() {
    return layout.length();
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
    if (!layout.variableLength() && record.length != layout.length()) {
      throw new IllegalArgumentException(
          layout.name() + ": expected " + layout.length() + " bytes, got " + record.length);
    }
    Ctx ctx = new Ctx(record);
    T value = type.cast(root.decode(ctx, 0));
    if (ctx.end != record.length) {
      throw new IllegalArgumentException(
          layout.name() + ": ODO counters describe " + ctx.end + " bytes, got " + record.length);
    }
    return value;
  }

  @Override
  public byte[] encode(T value) {
    byte[] out = new byte[layout.length()];
    Arrays.fill(out, options.space());
    Ctx ctx = new Ctx(out);
    root.encode(ctx, value, 0);
    return ctx.end < out.length ? Arrays.copyOf(out, ctx.end) : out;
  }

  /** Per-call state: buffer, decoded counters (for ODO) and the furthest byte touched. */
  private static final class Ctx {
    final byte[] buf;
    final Map<String, Object> counters = new HashMap<>();
    int end;

    Ctx(byte[] buf) {
      this.buf = buf;
    }

    void touch(int to) {
      end = Math.max(end, to);
    }

    int count(String counter) {
      Object v = counters.get(counter);
      if (v == null) {
        throw new IllegalStateException("ODO counter " + counter + " not decoded before table");
      }
      return FieldCodec.toBigDecimal(v).intValueExact();
    }
  }

  private interface Binder {
    Object decode(Ctx ctx, int shift);

    void encode(Ctx ctx, Object value, int shift);
  }

  private final class LeafBinder implements Binder {
    final LayoutNode node;
    final Class<?> target;

    LeafBinder(LayoutNode node, Class<?> target) {
      this.node = node;
      this.target = target;
    }

    @Override
    public Object decode(Ctx ctx, int shift) {
      int off = node.offset() + shift;
      if (off + node.length() > ctx.buf.length) {
        throw new IllegalArgumentException(
            layout.name() + ": record too short for " + node.name() + " (" + ctx.buf.length + ")");
      }
      Object v = FieldCodec.decode(node, ctx.buf, off, options, target);
      ctx.counters.put(node.name(), v);
      ctx.touch(off + node.length());
      return v;
    }

    @Override
    public void encode(Ctx ctx, Object value, int shift) {
      int off = node.offset() + shift;
      FieldCodec.encode(node, value, ctx.buf, off, options);
      ctx.counters.put(node.name(), value == null ? 0 : value);
      ctx.touch(off + node.length());
    }
  }

  private final class TableBinder implements Binder {
    final LayoutNode node;
    final Binder element;

    TableBinder(LayoutNode node, Binder element) {
      this.node = node;
      this.element = element;
    }

    int occurrences(Ctx ctx) {
      if (node.dependingOn() == null) {
        return node.occurs();
      }
      int n = ctx.count(node.dependingOn());
      if (n < node.minOccurs() || n > node.occurs()) {
        throw new IllegalArgumentException(
            node.name()
                + ": "
                + node.dependingOn()
                + "="
                + n
                + " outside "
                + node.minOccurs()
                + ".."
                + node.occurs());
      }
      return n;
    }

    @Override
    public Object decode(Ctx ctx, int shift) {
      int n = occurrences(ctx);
      List<Object> out = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        out.add(element.decode(ctx, shift + i * node.length()));
      }
      ctx.touch(node.offset() + shift + n * node.length());
      return List.copyOf(out);
    }

    @Override
    public void encode(Ctx ctx, Object value, int shift) {
      List<?> list = value == null ? List.of() : (List<?>) value;
      int n = node.dependingOn() == null ? node.occurs() : occurrences(ctx);
      if (node.dependingOn() != null && list.size() != n) {
        throw new IllegalArgumentException(
            node.name() + ": " + list.size() + " elements but " + node.dependingOn() + "=" + n);
      }
      if (list.size() > n) {
        throw new IllegalArgumentException(node.name() + ": more than " + n + " elements");
      }
      for (int i = 0; i < n; i++) {
        element.encode(ctx, i < list.size() ? list.get(i) : null, shift + i * node.length());
      }
      ctx.touch(node.offset() + shift + n * node.length());
    }
  }

  private final class GroupBinder implements Binder {
    final LayoutNode node;
    final RecordComponent[] components;
    final Binder[] children;
    final Constructor<?> constructor;

    GroupBinder(LayoutNode node, Class<?> recordType) {
      if (!recordType.isRecord()) {
        throw new IllegalArgumentException(recordType + " is not a record (" + node.name() + ")");
      }
      this.node = node;
      List<LayoutNode> items = node.storageChildren();
      if (items.isEmpty() && node.picture() != null) {
        items = List.of(node);
      }
      this.components = recordType.getRecordComponents();
      if (components.length != items.size()) {
        throw new IllegalArgumentException(
            recordType.getSimpleName()
                + " has "
                + components.length
                + " components, "
                + node.name()
                + " has "
                + items.size()
                + " items");
      }
      this.children = new Binder[items.size()];
      for (int i = 0; i < items.size(); i++) {
        children[i] = bind(items.get(i), components[i].getType(), components[i].getGenericType());
        if (items.get(i).dependingOn() != null && i != items.size() - 1) {
          throw new IllegalArgumentException(
              items.get(i).name() + ": OCCURS DEPENDING ON must be the last item of its group");
        }
      }
      try {
        this.constructor =
            recordType.getDeclaredConstructor(
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
        this.constructor.setAccessible(true);
        for (RecordComponent c : components) {
          c.getAccessor().setAccessible(true);
        }
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public Object decode(Ctx ctx, int shift) {
      Object[] args = new Object[children.length];
      for (int i = 0; i < args.length; i++) {
        args[i] = children[i].decode(ctx, shift);
      }
      try {
        return constructor.newInstance(args);
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public void encode(Ctx ctx, Object value, int shift) {
      for (int i = 0; i < children.length; i++) {
        Object v;
        try {
          v = value == null ? null : components[i].getAccessor().invoke(value);
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new IllegalStateException(e);
        }
        children[i].encode(ctx, v, shift);
      }
      if (!variable(node)) {
        ctx.touch(node.offset() + shift + node.length());
      }
    }
  }

  private static boolean variable(LayoutNode n) {
    return n.dependingOn() != null || n.children().stream().anyMatch(LayoutCodec::variable);
  }

  private Binder bind(LayoutNode n, Class<?> raw, Type generic) {
    if (n.table()) {
      if (!List.class.isAssignableFrom(raw) || !(generic instanceof ParameterizedType pt)) {
        throw new IllegalArgumentException(n.name() + " (OCCURS) must map to List<E>");
      }
      Type arg = pt.getActualTypeArguments()[0];
      Class<?> element = arg instanceof Class<?> c ? c : Object.class;
      return new TableBinder(n, single(n, element));
    }
    return single(n, raw);
  }

  private Binder single(LayoutNode n, Class<?> type) {
    if (n.group()) {
      return new GroupBinder(n, type);
    }
    return new LeafBinder(n, type);
  }
}
