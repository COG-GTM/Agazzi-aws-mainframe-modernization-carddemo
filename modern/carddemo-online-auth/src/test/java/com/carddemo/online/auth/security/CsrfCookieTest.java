package com.carddemo.online.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** CSRF double-submit cookie of the O1 chain (Fase 5A): readable by the SPA, SameSite=Strict. */
class CsrfCookieTest {

  @Test
  void everyResponseCarriesTheXsrfTokenCookie() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/screens/cosgn00");
    var response = new MockHttpServletResponse();
    var deferred =
        OnlineAuthSecurityConfiguration.csrfTokens().loadDeferredToken(request, response);
    new CsrfTokenRequestAttributeHandler().handle(request, response, deferred::get);
    new OnlineAuthSecurityConfiguration.CsrfCookieFilter()
        .doFilter(request, response, new MockFilterChain());
    var cookie = response.getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    assertThat(cookie.isHttpOnly()).isFalse();
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    assertThat(cookie.getValue()).isEqualTo(token.getToken());
    assertThat(token.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
  }
}
