package com.carddemo.datamigration.validate;

import com.carddemo.datamigration.model.Dataset;

/** One validation finding on a source record. */
public record Violation(
    Dataset dataset, long ordinal, Rule rule, Severity severity, String field, String message) {

  /** Validation rules of {@code vsamToRelational} (plan §5 1B). */
  public enum Rule {
    DECODE,
    DUPLICATE_KEY,
    ORPHAN_FK,
    INVALID_DATE,
    LOW_VALUES
  }

  /** ERROR blocks the load (or rejects the record with {@code --on-error=skip}); WARN reports. */
  public enum Severity {
    ERROR,
    WARN
  }
}
