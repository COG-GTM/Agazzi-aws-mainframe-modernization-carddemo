package com.carddemo.ext.authorization.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code carddemo.extensions.authorization.*}. */
@ConfigurationProperties(prefix = "carddemo.extensions.authorization")
public record AuthorizationProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("carddemo-auth-request") String requestChannel,
    @DefaultValue("carddemo-auth-reply") String replyChannel,
    @DefaultValue("true") boolean listenerEnabled,
    @DefaultValue("PT5S") java.time.Duration waitInterval) {}
