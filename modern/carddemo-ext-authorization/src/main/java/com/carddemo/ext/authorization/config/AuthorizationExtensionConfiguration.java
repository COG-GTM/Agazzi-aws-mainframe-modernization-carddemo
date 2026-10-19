package com.carddemo.ext.authorization.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Entry point of the X1 extension (picked up by the {@code com.carddemo} component scan). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAuthorizationExtension
@EnableConfigurationProperties(AuthorizationProperties.class)
public class AuthorizationExtensionConfiguration {}
