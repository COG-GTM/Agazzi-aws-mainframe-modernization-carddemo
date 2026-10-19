package com.carddemo.ext.authorization.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Active only with {@code carddemo.extensions.authorization.enabled=true} (default off). */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
    prefix = "carddemo.extensions.authorization",
    name = "enabled",
    havingValue = "true")
public @interface ConditionalOnAuthorizationExtension {}
