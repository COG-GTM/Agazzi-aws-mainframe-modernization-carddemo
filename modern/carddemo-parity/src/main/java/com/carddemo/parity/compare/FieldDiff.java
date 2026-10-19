package com.carddemo.parity.compare;

/** One difference: program/job, file, 1-based record number, copybook field, expected, actual. */
public record FieldDiff(
    String job, String file, int record, String field, String expected, String actual) {

  @Override
  public String toString() {
    return "%s %s record %d field %s: expected [%s] got [%s]"
        .formatted(job, file, record, field, expected, actual);
  }
}
