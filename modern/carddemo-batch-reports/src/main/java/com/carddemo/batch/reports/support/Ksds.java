package com.carddemo.batch.reports.support;

import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/** VSAM KSDS emulation: records kept in key order, unique keys, keyed random read. */
public final class Ksds {

  private final NavigableMap<String, String> records = new TreeMap<>();
  private final int keyOffset;
  private final int keyLength;

  private Ksds(int keyOffset, int keyLength) {
    this.keyOffset = keyOffset;
    this.keyLength = keyLength;
  }

  /**
   * IDCAMS REPRO into an empty cluster ({@code KEYS(keyLength keyOffset)}): a duplicate key ends
   * the REPRO with an error, which is reported as an abend of the calling step.
   */
  public static Ksds load(String dataset, List<String> input, int keyOffset, int keyLength) {
    Ksds k = new Ksds(keyOffset, keyLength);
    for (String r : input) {
      if (k.records.putIfAbsent(k.keyOf(r), r) != null) {
        throw new JobAbendException("IDCAMS", "duplicate key " + k.keyOf(r) + " in " + dataset);
      }
    }
    return k;
  }

  public String keyOf(String record) {
    return RecordSort.slice(record, keyOffset + 1, keyLength);
  }

  /** {@code READ ... KEY IS}: null when the key is not found (status 23). */
  public String read(String key) {
    return records.get(FixedRecords.pad(key, keyLength));
  }

  /** Sequential read in key order. */
  public Collection<String> inKeyOrder() {
    return records.values();
  }

  public int size() {
    return records.size();
  }
}
