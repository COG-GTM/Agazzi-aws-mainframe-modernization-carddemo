package com.carddemo.batch.posting.dataset;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * KSDS backed by a contracts-v1 table through its Spring Data repository. {@code REWRITE} updates
 * the managed entity in place so the optimistic-lock {@code version} is honoured.
 */
public final class JpaKeyedDataset<R, E, I> implements KeyedDataset<R> {

  private final String name;
  private final JpaRepository<E, I> repository;
  private final Function<String, I> idOfKey;
  private final Function<R, String> keyFn;
  private final Function<E, R> toRecord;
  private final Function<R, E> fromRecord;
  private final BiConsumer<E, R> updater;

  public JpaKeyedDataset(
      String name,
      JpaRepository<E, I> repository,
      Function<String, I> idOfKey,
      Function<R, String> keyFn,
      Function<E, R> toRecord,
      Function<R, E> fromRecord,
      BiConsumer<E, R> updater) {
    this.name = name;
    this.repository = repository;
    this.idOfKey = idOfKey;
    this.keyFn = keyFn;
    this.toRecord = toRecord;
    this.fromRecord = fromRecord;
    this.updater = updater;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void open() {
    repository.count();
  }

  @Override
  public Optional<R> read(String key) {
    return repository.findById(idOfKey.apply(key)).map(toRecord);
  }

  @Override
  public void write(R record) {
    String key = keyFn.apply(record);
    if (repository.existsById(idOfKey.apply(key))) {
      throw new DuplicateKeyException(name, key);
    }
    repository.save(fromRecord.apply(record));
  }

  @Override
  public void rewrite(R record) {
    String key = keyFn.apply(record);
    if (updater == null) {
      throw new UnsupportedOperationException("REWRITE not supported on " + name);
    }
    E entity =
        repository
            .findById(idOfKey.apply(key))
            .orElseThrow(() -> new RecordNotFoundException(name, key));
    updater.accept(entity, record);
    repository.save(entity);
  }

  @Override
  public List<R> records() {
    return repository.findAll().stream().map(toRecord).sorted(Comparator.comparing(keyFn)).toList();
  }

  @Override
  public void clear() {
    repository.deleteAllInBatch();
  }

  @Override
  public String keyOf(R record) {
    return keyFn.apply(record);
  }
}
