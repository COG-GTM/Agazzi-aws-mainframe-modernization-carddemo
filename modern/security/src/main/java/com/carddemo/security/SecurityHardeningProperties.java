package com.carddemo.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code carddemo.security.*}: response headers and audit log switches. */
@ConfigurationProperties("carddemo.security")
public class SecurityHardeningProperties {

  private final Headers headers = new Headers();
  private final Audit audit = new Audit();

  public Headers getHeaders() {
    return headers;
  }

  public Audit getAudit() {
    return audit;
  }

  /** Response security headers (ASVS V14.4). */
  public static class Headers {
    private boolean enabled = true;
    private String contentSecurityPolicy =
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
            + " connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self';"
            + " frame-ancestors 'none'";
    private String referrerPolicy = "no-referrer";
    private String permissionsPolicy =
        "camera=(), microphone=(), geolocation=(), payment=(), usb=()";
    private Duration hstsMaxAge = Duration.ofDays(365);
    private boolean hstsIncludeSubDomains = true;
    private boolean hstsPreload;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getContentSecurityPolicy() {
      return contentSecurityPolicy;
    }

    public void setContentSecurityPolicy(String contentSecurityPolicy) {
      this.contentSecurityPolicy = contentSecurityPolicy;
    }

    public String getReferrerPolicy() {
      return referrerPolicy;
    }

    public void setReferrerPolicy(String referrerPolicy) {
      this.referrerPolicy = referrerPolicy;
    }

    public String getPermissionsPolicy() {
      return permissionsPolicy;
    }

    public void setPermissionsPolicy(String permissionsPolicy) {
      this.permissionsPolicy = permissionsPolicy;
    }

    public Duration getHstsMaxAge() {
      return hstsMaxAge;
    }

    public void setHstsMaxAge(Duration hstsMaxAge) {
      this.hstsMaxAge = hstsMaxAge;
    }

    public boolean isHstsIncludeSubDomains() {
      return hstsIncludeSubDomains;
    }

    public void setHstsIncludeSubDomains(boolean hstsIncludeSubDomains) {
      this.hstsIncludeSubDomains = hstsIncludeSubDomains;
    }

    public boolean isHstsPreload() {
      return hstsPreload;
    }

    public void setHstsPreload(boolean hstsPreload) {
      this.hstsPreload = hstsPreload;
    }
  }

  /** Structured audit log on logger {@code carddemo.audit} (ASVS V7). */
  public static class Audit {
    private boolean enabled = true;
    private boolean includeNavigation;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /** Also audit GET first-send of screens and menus (noisy, off by default). */
    public boolean isIncludeNavigation() {
      return includeNavigation;
    }

    public void setIncludeNavigation(boolean includeNavigation) {
      this.includeNavigation = includeNavigation;
    }
  }
}
