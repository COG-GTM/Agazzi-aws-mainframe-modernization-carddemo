package com.carddemo.online.useradmin.support;

import com.carddemo.online.useradmin.store.UserRow;
import com.carddemo.online.useradmin.store.UserStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** USRSEC in memory, keyed in byte order like the VSAM KSDS. */
public class InMemoryUserStore implements UserStore {

  private final TreeMap<String, UserRow> rows = new TreeMap<>();
  private RuntimeException failure;

  /** The 10 users of {@code V2__seed_sample_data.sql} (plaintext legacy passwords). */
  public static InMemoryUserStore seeded() {
    InMemoryUserStore s = new InMemoryUserStore();
    for (int i = 1; i <= 5; i++) {
      s.put("ADMIN00" + i, "ADMIN", "00" + i, "PASSWORD", "A");
    }
    for (int i = 1; i <= 5; i++) {
      s.put("USER000" + i, "USER", "000" + i, "PASSWORD", "U");
    }
    return s;
  }

  /** Back to the V2 seed plus {@code extra} regular users {@code U0000001..}. */
  public synchronized void reseed(int extra) {
    rows.clear();
    failure = null;
    InMemoryUserStore seed = seeded();
    rows.putAll(seed.rows);
    for (int i = 1; i <= extra; i++) {
      put(String.format("U%07d", i), "F" + i, "L" + i, "PASSWORD", "U");
    }
  }

  public void put(String id, String fname, String lname, String pwd, String type) {
    rows.put(
        Fields.pad(id, 8),
        new UserRow(
            Fields.pad(id, 8),
            Fields.pad(fname, 20),
            Fields.pad(lname, 20),
            pwd,
            type,
            Fields.pad("", 23),
            0L));
  }

  /** Every following call fails with RESP OTHER. */
  public void failWith(RuntimeException e) {
    failure = e;
  }

  private void check() {
    if (failure != null) {
      throw failure;
    }
  }

  public Map<String, UserRow> snapshot() {
    return Collections.unmodifiableMap(new TreeMap<>(rows));
  }

  @Override
  public synchronized Optional<UserRow> findById(String id) {
    check();
    return Optional.ofNullable(rows.get(id));
  }

  @Override
  public synchronized List<UserRow> browseForward(String fromKey, int limit) {
    check();
    List<UserRow> out = new ArrayList<>();
    for (UserRow r : rows.tailMap(fromKey, true).values()) {
      if (out.size() == limit) {
        break;
      }
      out.add(r);
    }
    return out;
  }

  @Override
  public synchronized List<UserRow> browseBackward(String fromKey, int limit) {
    check();
    List<UserRow> out = new ArrayList<>();
    for (UserRow r : rows.headMap(fromKey, true).descendingMap().values()) {
      if (out.size() == limit) {
        break;
      }
      out.add(r);
    }
    return out;
  }

  @Override
  public synchronized WriteOutcome insert(UserRow row) {
    check();
    if (rows.containsKey(row.id())) {
      return WriteOutcome.DUPLICATE;
    }
    rows.put(row.id(), row);
    return WriteOutcome.OK;
  }

  @Override
  public synchronized WriteOutcome update(UserRow row) {
    check();
    UserRow cur = rows.get(row.id());
    if (cur == null) {
      return WriteOutcome.NOT_FOUND;
    }
    if (cur.version() != row.version()) {
      return WriteOutcome.CONFLICT;
    }
    rows.put(
        row.id(),
        new UserRow(
            row.id(),
            row.firstName(),
            row.lastName(),
            row.password(),
            row.type(),
            row.filler(),
            row.version() + 1));
    return WriteOutcome.OK;
  }

  @Override
  public synchronized WriteOutcome delete(UserRow row) {
    check();
    UserRow cur = rows.get(row.id());
    if (cur == null) {
      return WriteOutcome.NOT_FOUND;
    }
    if (cur.version() != row.version()) {
      return WriteOutcome.CONFLICT;
    }
    rows.remove(row.id());
    return WriteOutcome.OK;
  }
}
