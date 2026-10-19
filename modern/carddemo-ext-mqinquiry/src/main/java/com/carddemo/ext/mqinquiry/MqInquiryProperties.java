package com.carddemo.ext.mqinquiry;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the x3-mqinquiry extension. Queue names default to the legacy MQ names with dots
 * replaced by hyphens (SQS queue names only allow alphanumerics, hyphens and underscores).
 *
 * @param enabled feature flag {@code carddemo.extensions.mqinquiry.enabled} (default off)
 * @param listenerEnabled whether the request-queue pollers start with the context
 * @param dateRequestQueue CDRD trigger/input queue (legacy MQTM-QNAME)
 * @param dateReplyQueue CODATE01 reply queue (legacy {@code CARD.DEMO.REPLY.DATE})
 * @param accountRequestQueue CDRA trigger/input queue (legacy MQTM-QNAME)
 * @param accountReplyQueue COACCT01 reply queue (legacy {@code CARD.DEMO.REPLY.ACCT})
 * @param errorQueue error queue (legacy {@code CARD.DEMO.ERROR})
 * @param pollWait MQGET wait interval (legacy MQGMO-WAITINTERVAL 5000 ms)
 * @param honorReplyTo reply to the request {@code replyTo} header when present instead of the fixed
 *     program reply queue (default false = legacy behaviour; enable for concurrent callers that use
 *     a private reply queue each)
 */
@ConfigurationProperties(prefix = "carddemo.extensions.mqinquiry")
public record MqInquiryProperties(
    boolean enabled,
    Boolean listenerEnabled,
    String dateRequestQueue,
    String dateReplyQueue,
    String accountRequestQueue,
    String accountReplyQueue,
    String errorQueue,
    Duration pollWait,
    boolean honorReplyTo) {

  public MqInquiryProperties {
    listenerEnabled = listenerEnabled == null || listenerEnabled;
    dateRequestQueue = orDefault(dateRequestQueue, "CARD-DEMO-REQUEST-DATE");
    dateReplyQueue = orDefault(dateReplyQueue, "CARD-DEMO-REPLY-DATE");
    accountRequestQueue = orDefault(accountRequestQueue, "CARD-DEMO-REQUEST-ACCT");
    accountReplyQueue = orDefault(accountReplyQueue, "CARD-DEMO-REPLY-ACCT");
    errorQueue = orDefault(errorQueue, "CARD-DEMO-ERROR");
    pollWait = pollWait == null ? Duration.ofMillis(5000) : pollWait;
  }

  /** Defaults, as bound when no property is set. */
  public static MqInquiryProperties defaults() {
    return new MqInquiryProperties(false, null, null, null, null, null, null, null, false);
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
