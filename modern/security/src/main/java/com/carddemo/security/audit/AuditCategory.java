package com.carddemo.security.audit;

import java.util.Locale;
import java.util.Map;

/** Audit category of an API path (one entry per BMS map of openapi.yaml, plus platform paths). */
public enum AuditCategory {
  SIGNON,
  NAVIGATION,
  ADMIN,
  UPDATE,
  READ,
  JOB,
  UNKNOWN;

  private static final String SCREENS = "/api/v1/screens/";

  /** Map id (lower case, as in /api/v1/screens/{map}) to category. */
  static final Map<String, AuditCategory> SCREEN_CATEGORIES =
      Map.ofEntries(
          Map.entry("cosgn00", SIGNON),
          Map.entry("comen01", NAVIGATION),
          Map.entry("coadm01", ADMIN),
          Map.entry("cousr00", ADMIN),
          Map.entry("cousr01", ADMIN),
          Map.entry("cousr02", ADMIN),
          Map.entry("cousr03", ADMIN),
          Map.entry("cotrtli", ADMIN),
          Map.entry("cotrtup", ADMIN),
          Map.entry("coactup", UPDATE),
          Map.entry("cocrdup", UPDATE),
          Map.entry("cotrn02", UPDATE),
          Map.entry("cobil00", UPDATE),
          Map.entry("copau01", UPDATE),
          Map.entry("corpt00", JOB),
          Map.entry("coactvw", READ),
          Map.entry("cocrdli", READ),
          Map.entry("cocrdsl", READ),
          Map.entry("cotrn00", READ),
          Map.entry("cotrn01", READ),
          Map.entry("copau00", READ));

  /** Screen id of a {@code /api/v1/screens/{map}} path, or {@code null}. */
  public static String screenOf(String path) {
    if (!path.startsWith(SCREENS)) {
      return null;
    }
    String rest = path.substring(SCREENS.length());
    int slash = rest.indexOf('/');
    String id = (slash < 0 ? rest : rest.substring(0, slash)).toLowerCase(Locale.ROOT);
    return id.matches("[a-z0-9]{1,8}") ? id : null;
  }

  public static AuditCategory of(String path) {
    String screen = screenOf(path);
    if (screen != null) {
      return SCREEN_CATEGORIES.getOrDefault(screen, UNKNOWN);
    }
    if (path.startsWith("/api/v1/jobs/")) {
      return JOB;
    }
    if (path.startsWith("/api/v1/ext/mqinquiry/")) {
      return READ;
    }
    if (path.startsWith("/actuator/maintenance")) {
      return ADMIN;
    }
    return UNKNOWN;
  }
}
