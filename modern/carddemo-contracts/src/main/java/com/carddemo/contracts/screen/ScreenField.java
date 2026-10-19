package com.carddemo.contracts.screen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Position and length of a BMS field (DFHMDF POS/LENGTH); {@code input} = UNPROT. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
public @interface ScreenField {
  int row();

  int column();

  int length();

  boolean input() default false;
}
