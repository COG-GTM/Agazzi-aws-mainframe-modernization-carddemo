package com.carddemo.contracts.copybook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a record DTO as the Java view of a copybook 01-level of fixed length. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CobolRecord {
  /** Copybook member name under app/cpy (e.g. CVACT01Y). */
  String copybook();

  /** Record length in bytes (LRECL). */
  int length();
}
