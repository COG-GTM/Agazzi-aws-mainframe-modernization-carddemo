package com.carddemo.batch.datalifecycle.program;

import java.io.Closeable;

/** Sequential writer of records. */
public interface RecordSink extends Closeable {

  void write(byte[] record) throws FileStatusException;

  @Override
  void close() throws FileStatusException;
}
