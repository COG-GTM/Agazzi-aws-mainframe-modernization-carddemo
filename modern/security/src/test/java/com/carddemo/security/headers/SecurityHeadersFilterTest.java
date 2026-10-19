package com.carddemo.security.headers;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.security.SecurityHardeningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

  private final SecurityHeadersFilter filter =
      new SecurityHeadersFilter(new SecurityHardeningProperties.Headers());

  private MockHttpServletResponse run(MockHttpServletRequest req) throws Exception {
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req, res, new MockFilterChain());
    return res;
  }

  @Test
  void hstsOnlyOverTls() throws Exception {
    MockHttpServletRequest plain = new MockHttpServletRequest("GET", "/api/v1/screens/cosgn00");
    assertThat(run(plain).getHeader("Strict-Transport-Security")).isNull();
    MockHttpServletRequest tls = new MockHttpServletRequest("GET", "/api/v1/screens/cosgn00");
    tls.setSecure(true);
    assertThat(run(tls).getHeader("Strict-Transport-Security"))
        .isEqualTo("max-age=31536000; includeSubDomains");
  }

  @Test
  void baselineHeadersAndNoStoreOnApi() throws Exception {
    MockHttpServletResponse res =
        run(new MockHttpServletRequest("POST", "/api/v1/screens/coactvw"));
    assertThat(res.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
    assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(res.getHeader("X-Frame-Options")).isEqualTo("DENY");
    assertThat(res.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(res.getHeader("Permissions-Policy")).contains("camera=()");
    assertThat(res.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(
            run(new MockHttpServletRequest("GET", "/actuator/health")).getHeader("Cache-Control"))
        .isNull();
  }

  @Test
  void preloadIsOptIn() {
    var cfg = new SecurityHardeningProperties.Headers();
    cfg.setHstsPreload(true);
    assertThat(new SecurityHeadersFilter(cfg).hstsValue()).endsWith("; preload");
  }
}
