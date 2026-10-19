package com.carddemo.datamigration.io;

/** The extract cannot be split into records of the copybook length. */
public class MalformedSourceException extends RuntimeException {
  public MalformedSourceException(String message) {
    super(message);
  }
}
