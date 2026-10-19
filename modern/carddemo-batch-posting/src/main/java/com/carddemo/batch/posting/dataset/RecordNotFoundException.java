package com.carddemo.batch.posting.dataset;

/** VSAM file status 23: no record with the requested key. */
public class RecordNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RecordNotFoundException(String dataset, String key) {
    super("record " + key.strip() + " not found in " + dataset);
  }
}
