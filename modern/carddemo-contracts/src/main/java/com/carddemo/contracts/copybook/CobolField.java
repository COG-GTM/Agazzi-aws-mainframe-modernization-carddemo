package com.carddemo.contracts.copybook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Copybook layout of a record component: COBOL name, PICTURE (DISPLAY usage), offset and length in
 * bytes. Used by codecs and by the field-level parity comparators.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
public @interface CobolField {
  String name();

  String pic();

  int offset();

  int length();
}
