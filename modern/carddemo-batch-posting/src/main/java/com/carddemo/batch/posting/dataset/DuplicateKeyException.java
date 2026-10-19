package com.carddemo.batch.posting.dataset;

/** VSAM file status 22: a record with the same primary key already exists. */
public class DuplicateKeyException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateKeyException(String dataset, String key) {
    super("duplicate key " + key.strip() + " in " + dataset);
  }
}
