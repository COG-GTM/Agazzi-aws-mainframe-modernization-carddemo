package com.carddemo.batch.datalifecycle.program;

import java.io.Closeable;
import java.util.Optional;

/** Sequential reader over fixed-length records (a KSDS read in key order or a PS file). */
public interface RecordSource extends Closeable {

  /** Next record, or empty at end of file. */
  Optional<byte[]> next() throws FileStatusException;

  @Override
  void close() throws FileStatusException;
}
