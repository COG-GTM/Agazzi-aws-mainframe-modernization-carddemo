package com.carddemo.ext.trantype.support;

import com.carddemo.ext.trantype.store.CategoryRow;
import com.carddemo.ext.trantype.store.Pic;
import com.carddemo.ext.trantype.store.TransactionTypeStore;
import com.carddemo.ext.trantype.store.TrantypeSqlException;
import com.carddemo.ext.trantype.store.TypeFilter;
import com.carddemo.ext.trantype.store.TypeRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Store over sorted maps, seeded from the shared V2 seed (same rows as the IT database). */
public class InMemoryTransactionTypeStore implements TransactionTypeStore {

  private static final Pattern TYPE_ROW =
      Pattern.compile("^\\('(\\d{2})', '([^']*)', '(\\d{8})'\\)[,;]$");
  private static final Pattern CAT_ROW =
      Pattern.compile("^\\('(\\d{2})', (\\d+), '([^']*)', '(\\d{4})'\\)[,;]$");

  public final TreeMap<String, String> types = new TreeMap<>();
  public final TreeMap<String, CategoryRow> categories = new TreeMap<>();
  public TrantypeSqlException failWith;

  public static InMemoryTransactionTypeStore seeded() {
    InMemoryTransactionTypeStore s = new InMemoryTransactionTypeStore();
    try (InputStream in =
        InMemoryTransactionTypeStore.class
            .getClassLoader()
            .getResourceAsStream("db/seed/V2__seed_sample_data.sql")) {
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      for (String line : sql.split("\n")) {
        Matcher t = TYPE_ROW.matcher(line.strip());
        if (t.matches()) {
          s.types.put(t.group(1), t.group(2));
        }
        Matcher c = CAT_ROW.matcher(line.strip());
        if (c.matches()) {
          int cat = Integer.parseInt(c.group(2));
          s.categories.put(
              c.group(1) + String.format("%04d", cat),
              new CategoryRow(c.group(1), cat, c.group(3), c.group(4)));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return s;
  }

  private void maybeFail() {
    if (failWith != null) {
      throw failWith;
    }
  }

  private boolean matches(TypeFilter f, Map.Entry<String, String> e) {
    return (f.typeCode() == null || f.typeCode().equals(e.getKey()))
        && (f.descriptionContains() == null || e.getValue().contains(f.descriptionContains()));
  }

  @Override
  public List<TypeRow> forward(TypeFilter filter, String startKey, int limit) {
    maybeFail();
    List<TypeRow> out = new ArrayList<>();
    for (Map.Entry<String, String> e : types.tailMap(startKey, true).entrySet()) {
      if (out.size() == limit) {
        break;
      }
      if (matches(filter, e)) {
        out.add(new TypeRow(e.getKey(), e.getValue()));
      }
    }
    return out;
  }

  @Override
  public List<TypeRow> backward(TypeFilter filter, String startKey, int limit) {
    maybeFail();
    List<TypeRow> out = new ArrayList<>();
    for (Map.Entry<String, String> e : types.headMap(startKey, false).descendingMap().entrySet()) {
      if (out.size() == limit) {
        break;
      }
      if (matches(filter, e)) {
        out.add(new TypeRow(e.getKey(), e.getValue()));
      }
    }
    return out;
  }

  @Override
  public long count(TypeFilter filter) {
    maybeFail();
    return types.entrySet().stream().filter(e -> matches(filter, e)).count();
  }

  @Override
  public Optional<TypeRow> find(String type) {
    maybeFail();
    return Optional.ofNullable(types.get(type)).map(d -> new TypeRow(type, d));
  }

  @Override
  public void insert(String type, String description) {
    maybeFail();
    if (types.containsKey(type)) {
      throw new TrantypeSqlException(
          TrantypeSqlException.DUPLICATE_KEY, TrantypeSqlException.DUPLICATE_KEY_TEXT);
    }
    types.put(type, Pic.pad(description, Pic.DESC_LEN));
  }

  @Override
  public void updateDescription(String type, String description) {
    maybeFail();
    if (!types.containsKey(type)) {
      throw new TrantypeSqlException(
          TrantypeSqlException.NOT_FOUND, TrantypeSqlException.NOT_FOUND_TEXT);
    }
    types.put(type, Pic.pad(description, Pic.DESC_LEN));
  }

  @Override
  public void delete(String type) {
    maybeFail();
    if (!types.containsKey(type)) {
      throw new TrantypeSqlException(
          TrantypeSqlException.NOT_FOUND, TrantypeSqlException.NOT_FOUND_TEXT);
    }
    if (categories.values().stream().anyMatch(c -> c.type().equals(type))) {
      throw new TrantypeSqlException(
          TrantypeSqlException.CHILD_ROWS_EXIST, TrantypeSqlException.CHILD_ROWS_TEXT);
    }
    types.remove(type);
  }

  @Override
  public List<TypeRow> allTypes() {
    maybeFail();
    return types.entrySet().stream().map(e -> new TypeRow(e.getKey(), e.getValue())).toList();
  }

  @Override
  public List<CategoryRow> allCategories() {
    maybeFail();
    return List.copyOf(categories.values());
  }

  /** {@code KIND transaction_type key} entries between two snapshots of {@link #types}. */
  public static List<String> delta(Map<String, String> before, Map<String, String> after) {
    List<String> out = new ArrayList<>();
    TreeMap<String, String> keys = new TreeMap<>(before);
    keys.putAll(after);
    for (String k : keys.keySet()) {
      if (!after.containsKey(k)) {
        out.add("DELETE transaction_type " + k);
      } else if (!before.containsKey(k)) {
        out.add("INSERT transaction_type " + k);
      } else if (!before.get(k).equals(after.get(k))) {
        out.add("UPDATE transaction_type " + k);
      }
    }
    return out;
  }
}
