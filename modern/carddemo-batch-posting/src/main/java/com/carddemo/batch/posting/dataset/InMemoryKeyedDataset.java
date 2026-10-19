package com.carddemo.batch.posting.dataset;

import com.carddemo.batch.posting.support.CobolAbendException;
import com.carddemo.contracts.port.RecordCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * KSDS emulated as a sorted map, loaded from / unloaded to a line-sequential file (the IDCAMS REPRO
 * image of the cluster). Changes become visible on disk only on {@link #flush()}, so an abended
 * step leaves the dataset untouched and can be restarted.
 */
public final class InMemoryKeyedDataset<R> implements KeyedDataset<R> {

  private final String name;
  private final Path file;
  private final RecordCodec<R> codec;
  private final Function<R, String> keyFn;
  private final boolean mustExist;
  private TreeMap<String, R> data;
  private boolean dirty;

  public InMemoryKeyedDataset(
      String name, Path file, RecordCodec<R> codec, Function<R, String> keyFn, boolean mustExist) {
    this.name = name;
    this.file = file;
    this.codec = codec;
    this.keyFn = keyFn;
    this.mustExist = mustExist;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void open() {
    data();
  }

  private TreeMap<String, R> data() {
    if (data == null) {
      TreeMap<String, R> loaded = new TreeMap<>();
      if (file != null && Files.exists(file)) {
        int n = 0;
        for (String line : LineSequentialFile.read(file, codec.recordLength())) {
          R r = CodecSupport.decode(codec, line, name, ++n);
          if (loaded.put(keyFn.apply(r), r) != null) {
            throw new CobolAbendException(
                "DUPLICATE KEY " + keyFn.apply(r).strip() + " LOADING " + name, "22");
          }
        }
      } else if (mustExist) {
        throw new CobolAbendException("ERROR OPENING " + name, "35");
      }
      data = loaded;
    }
    return data;
  }

  @Override
  public Optional<R> read(String key) {
    return Optional.ofNullable(data().get(key));
  }

  @Override
  public void write(R record) {
    String key = keyFn.apply(record);
    if (data().containsKey(key)) {
      throw new DuplicateKeyException(name, key);
    }
    data().put(key, record);
    dirty = true;
  }

  @Override
  public void rewrite(R record) {
    String key = keyFn.apply(record);
    if (!data().containsKey(key)) {
      throw new RecordNotFoundException(name, key);
    }
    data().put(key, record);
    dirty = true;
  }

  @Override
  public List<R> records() {
    return new ArrayList<>(data().values());
  }

  @Override
  public void clear() {
    data = new TreeMap<>();
    dirty = true;
  }

  @Override
  public String keyOf(R record) {
    return keyFn.apply(record);
  }

  public boolean isDirty() {
    return dirty;
  }

  /** Writes the cluster back (in key sequence) if it was modified. */
  public void flush() {
    if (dirty && file != null) {
      LineSequentialFile.replaceAll(Map.of(file, encodedRecords()));
      dirty = false;
    }
  }

  Path file() {
    return file;
  }

  List<String> encodedRecords() {
    return data().values().stream().map(codec::encodeLine).toList();
  }

  void markClean() {
    dirty = false;
  }
}
