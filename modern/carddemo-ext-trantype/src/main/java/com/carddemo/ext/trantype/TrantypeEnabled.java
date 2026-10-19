package com.carddemo.ext.trantype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Beans of the X2 extension exist only with {@code carddemo.extensions.trantype.enabled=true}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnProperty(
    prefix = "carddemo.extensions.trantype",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public @interface TrantypeEnabled {
  String PROPERTY = "carddemo.extensions.trantype.enabled";
}
