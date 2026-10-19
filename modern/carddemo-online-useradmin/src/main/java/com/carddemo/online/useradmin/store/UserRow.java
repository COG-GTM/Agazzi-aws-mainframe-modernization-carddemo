package com.carddemo.online.useradmin.store;

/**
 * One USRSEC (CSUSR01Y) record as stored in {@code app_user}; PIC X fields keep their padding.
 * {@code password} is the stored credential (BCrypt hash or legacy seed value) and never leaves the
 * service layer.
 */
public record UserRow(
    String id,
    String firstName,
    String lastName,
    String password,
    String type,
    String filler,
    long version) {

  @Override
  public String toString() {
    return "UserRow[id=" + id + ", type=" + type + ", version=" + version + "]";
  }
}
