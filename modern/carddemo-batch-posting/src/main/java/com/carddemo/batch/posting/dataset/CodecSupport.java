package com.carddemo.batch.posting.dataset;

import com.carddemo.batch.posting.support.CobolAbendException;
import com.carddemo.contracts.port.RecordCodec;

/** Decoding of a record line, turning codec errors (non-numeric PIC 9 data) into an abend. */
public final class CodecSupport {

  private CodecSupport() {}

  public static <R> R decode(RecordCodec<R> codec, String line, String dataset, int recordNo) {
    try {
      return codec.decodeLine(line);
    } catch (RuntimeException e) {
      throw new CobolAbendException(
          "MALFORMED RECORD " + recordNo + " IN " + dataset + ": " + e.getMessage(), "04", e);
    }
  }
}
