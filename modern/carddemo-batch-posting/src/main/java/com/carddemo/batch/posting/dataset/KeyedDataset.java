package com.carddemo.batch.posting.dataset;

import java.util.List;
import java.util.Optional;

/**
 * Random-access view of a VSAM KSDS (READ / WRITE / REWRITE by primary key) over fixed-length
 * records. Keys are the encoded key bytes (e.g. {@code 00000000001} for ACCT-ID), so their natural
 * {@link String} order is the KSDS key sequence.
 */
public interface KeyedDataset<R> {

  /** DD / dataset name used in messages. */
  String name();

  /** {@code OPEN}: fails with file status 35 when the dataset does not exist. */
  void open();

  /** {@code READ ... KEY IS}: empty on INVALID KEY (status 23). */
  Optional<R> read(String key);

  /** {@code WRITE}: throws {@link DuplicateKeyException} on status 22. */
  void write(R record);

  /** {@code REWRITE}: throws {@link RecordNotFoundException} on status 23. */
  void rewrite(R record);

  /** All records in key sequence (sequential browse / IDCAMS REPRO). */
  List<R> records();

  /** Removes every record (IDCAMS DELETE CLUSTER + DEFINE CLUSTER). */
  void clear();

  /** Encoded primary key of a record. */
  String keyOf(R record);
}
