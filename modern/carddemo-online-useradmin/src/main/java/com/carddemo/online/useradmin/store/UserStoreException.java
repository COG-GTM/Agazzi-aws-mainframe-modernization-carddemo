package com.carddemo.online.useradmin.store;

/** CICS {@code RESP OTHER} on the USRSEC file. */
public class UserStoreException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UserStoreException(String message, Throwable cause) {
    super(message, cause);
  }
}
