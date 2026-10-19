package com.carddemo.online.transaction.file;

/** Outcome of a CICS READ: response code and, for NORMAL/DUPKEY, the record read. */
public record ReadResult<T>(Resp resp, T record) {

  public static <T> ReadResult<T> normal(T record) {
    return new ReadResult<>(Resp.NORMAL, record);
  }

  public static <T> ReadResult<T> of(Resp resp) {
    return new ReadResult<>(resp, null);
  }
}
