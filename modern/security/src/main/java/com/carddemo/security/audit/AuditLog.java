package com.carddemo.security.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Writes one structured audit record per event on logger {@code carddemo.audit} as SLF4J key/value
 * pairs (JSON in profile {@code aws} via logback {@code JsonEncoder}, {@code %kvp} elsewhere).
 * Values are sanitised: no CR/LF or control characters, bounded length. Callers never pass
 * passwords, request bodies, session ids or PII.
 */
public class AuditLog {

  public static final String LOGGER = "carddemo.audit";
  private static final Logger LOG = LoggerFactory.getLogger(LOGGER);
  private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._:@/,=+\\- ]");
  private static final int MAX = 128;

  /** Event names: AUTHENTICATION_SUCCESS/FAILURE, AUTHORIZATION_FAILURE, LOGOUT, ACCESS. */
  public void record(String event, Map<String, ?> fields) {
    LoggingEventBuilder b =
        LOG.atInfo().addKeyValue("audit", "carddemo").addKeyValue("event", event);
    for (Map.Entry<String, String> e : sanitize(fields).entrySet()) {
      b = b.addKeyValue(e.getKey(), e.getValue());
    }
    b.log("audit " + clean(event));
  }

  static Map<String, String> sanitize(Map<String, ?> fields) {
    Map<String, String> out = new LinkedHashMap<>();
    fields.forEach(
        (k, v) -> {
          if (v != null) {
            out.put(clean(k), clean(String.valueOf(v)));
          }
        });
    return out;
  }

  static String clean(String s) {
    String c = UNSAFE.matcher(s).replaceAll("_");
    return c.length() > MAX ? c.substring(0, MAX) : c;
  }
}
