package com.carddemo.online.transaction.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.online.transaction.service.Cotrn01cService;
import com.carddemo.online.transaction.service.ProgramResult;
import com.carddemo.platform.time.FixedBusinessClock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ScreenSupportTest {

  private final Cotrn01cService svc =
      new Cotrn01cService(
          Mockito.mock(com.carddemo.online.transaction.file.TransactFile.class),
          FixedBusinessClock.golden());

  private MockHttpServletRequest request(String user, ConversationContext ctx) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setUserPrincipal(new UsernamePasswordAuthenticationToken(user, "x"));
    if (ctx != null) {
      req.getSession(true).setAttribute(ConversationStore.CONTEXT_ATTRIBUTE, ctx);
    }
    return req;
  }

  private static ConversationContext ctx(String user) {
    return ConversationContext.signedOn(user, UserType.USER)
        .transferTo("CM00", "COMEN01C", "CT01", "COTRN01C");
  }

  @Test
  void runsWithTheSessionCommarea() {
    var req = request("USER0001", ctx("USER0001"));
    var r = ScreenSupport.firstSend(req, new MockHttpServletResponse(), ScreenId.COTRN01, svc::run);
    assertThat(r.nextScreen()).isNull();
    assertThat(r.context().userId()).isEqualTo("USER0001");
    assertThat(ConversationStore.context(req.getSession())).isEqualTo(r.context());
  }

  @Test
  void noCommareaGoesToSignOn() {
    var r =
        ScreenSupport.firstSend(
            request("USER0001", null), new MockHttpServletResponse(), ScreenId.COTRN01, svc::run);
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COSGN00);
  }

  @Test
  void commareaOfAnotherUserIsDiscarded() {
    var req = request("USER0002", ctx("USER0001"));
    var r = ScreenSupport.firstSend(req, new MockHttpServletResponse(), ScreenId.COTRN01, svc::run);
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COSGN00);
    assertThat(ConversationStore.context(req.getSession())).isNull();
  }

  @Test
  void reportExecutionIdIsExposed() {
    var req = request("USER0001", ctx("USER0001"));
    var resp = new MockHttpServletResponse();
    ScreenSupport.Program<Object> p =
        (c, e, a, i) ->
            new ProgramResult<>(
                new com.carddemo.contracts.screen.ScreenResponse<>(
                    ScreenId.CORPT00, null, java.util.List.of(), null, java.util.List.of(), c),
                e,
                42L);
    ScreenSupport.firstSend(req, resp, ScreenId.CORPT00, p);
    assertThat(resp.getHeader(ScreenSupport.REPORT_HEADER)).isEqualTo("42");
    assertThat(resp.getHeader("Location")).isEqualTo("/api/v1/jobs/42");
    assertThat(req.getAttribute(ScreenSupport.REPORT_HEADER)).isEqualTo(42L);
    var body =
        Corpt00Body.of(
            new com.carddemo.contracts.screen.ScreenResponse<>(
                ScreenId.CORPT00, null, java.util.List.of(), null, java.util.List.of(), null),
            req);
    assertThat(body.reportExecutionId()).isEqualTo(42L);
  }

  @Test
  void reportExecutionIdIsInTheBody() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var body =
        new Corpt00Body(
            new com.carddemo.contracts.screen.ScreenResponse<>(
                ScreenId.CORPT00, null, java.util.List.of(), null, java.util.List.of(), null),
            7L);
    var tree = json.readTree(json.writeValueAsString(body));
    assertThat(tree.get("reportExecutionId").asLong()).isEqualTo(7L);
    assertThat(tree.get("screen").asText()).isEqualTo("CORPT00");
    assertThat(tree.has("response")).isFalse();
  }
}
