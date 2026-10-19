package com.carddemo.online.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.domain.repository.AppUserRepository;
import com.carddemo.online.auth.AuthTestApplication;
import com.carddemo.online.auth.SeedUsers;
import com.carddemo.online.auth.security.AppUserCredentialService;
import com.carddemo.online.auth.session.ConversationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Spring Security + session behaviour of the O1 resources (ADR-002, plan Fase 3). */
@SpringBootTest(classes = AuthTestApplication.class)
@AutoConfigureMockMvc
class ScreenSecurityMockMvcTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired WebApplicationContext context;
  @Autowired UserCredentialService credentials;
  @MockBean AppUserRepository users;

  @BeforeEach
  void seed() {
    SeedUsers.stub(users);
  }

  static String body(String action, String fields) {
    return "{\"action\":\"" + action + "\",\"fields\":" + fields + "}";
  }

  private MockHttpSession signOn(String user) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/v1/screens/cosgn00")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body("ENTER", "{\"USERID\":\"" + user + "\",\"PASSWD\":\"PASSWORD\"}")))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  @Test
  void o1ReplacesTheFase0Fallbacks() {
    assertThat(credentials).isInstanceOf(AppUserCredentialService.class);
  }

  @Test
  void signOnScreenIsPublicAndOtherScreensAreNot() throws Exception {
    mvc.perform(get("/api/v1/screens/cosgn00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.screen").value("COSGN00"))
        .andExpect(jsonPath("$.data.TRNNAME").value("CC00"));
    mvc.perform(get("/api/v1/screens/comen01"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.nextScreen").value("COSGN00"))
        .andExpect(jsonPath("$.screen").value("COMEN01"));
    mvc.perform(get("/api/v1/screens/coactvw")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health"))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isNotIn(401, 403));
  }

  @Test
  void signOnCreatesSessionWithContextAndMenuFlow() throws Exception {
    MockHttpSession s = signOn("user0001");
    assertThat(s).isNotNull();
    assertThat(s.getAttribute(ConversationStore.ATTRIBUTE)).asString().contains("USER0001");
    mvc.perform(get("/api/v1/screens/comen01").session(s))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.OPTN001").value("01. Account View"))
        .andExpect(jsonPath("$.context.userId").value("USER0001"))
        .andExpect(jsonPath("$.context.programContext").value("REENTER"));
    mvc.perform(
            post("/api/v1/screens/comen01")
                .session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ENTER", "{\"OPTION\":\"6\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextScreen").value("COTRN00"))
        .andExpect(jsonPath("$.context.fromTransactionId").value("CM00"));
    assertThat(s.getAttribute(ConversationStore.ATTRIBUTE)).asString().contains("COTRN00C");
  }

  @Test
  void firstPostAfterSignOnOnlySendsTheMenu() throws Exception {
    MockHttpSession s = signOn("USER0002");
    mvc.perform(
            post("/api/v1/screens/comen01")
                .session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ENTER", "{\"OPTION\":\"1\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextScreen").doesNotExist())
        .andExpect(jsonPath("$.data.OPTN001").value("01. Account View"));
  }

  @Test
  void regularUserCannotUseTheAdminMenu() throws Exception {
    MockHttpSession s = signOn("USER0001");
    mvc.perform(get("/api/v1/screens/coadm01").session(s))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.messages[0].text").value("No access - Admin Only option..."))
        .andExpect(jsonPath("$.nextScreen").value("COMEN01"));
    mvc.perform(
            post("/api/v1/screens/coadm01")
                .with(httpBasic("USER0001", "PASSWORD"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ENTER", "{\"OPTION\":\"1\"}")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/screens/cousr01").with(httpBasic("USER0001", "PASSWORD")))
        .andExpect(status().isForbidden());
    MockHttpSession a = signOn("ADMIN001");
    mvc.perform(get("/api/v1/screens/coadm01").session(a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.OPTN001").value("01. User List (Security)"));
  }

  @Test
  void pf3OnMenuIsLogoutAndInvalidatesTheSession() throws Exception {
    MockHttpSession s = signOn("ADMIN002");
    mvc.perform(get("/api/v1/screens/coadm01").session(s)).andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/screens/coadm01")
                .session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PF3", "{}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextScreen").value("COSGN00"))
        .andExpect(jsonPath("$.context").doesNotExist());
    assertThat(s.isInvalid()).isTrue();
  }

  @Test
  void pf3OnSignOnEndsTheConversation() throws Exception {
    MockHttpSession s = signOn("USER0003");
    mvc.perform(
            post("/api/v1/screens/cosgn00")
                .session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PF3", "{}")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.messages[0].text").value("Thank you for using CardDemo application..."));
    assertThat(s.isInvalid()).isTrue();
  }

  @Test
  void signOnRotatesTheSessionId() throws Exception {
    MockHttpSession pre = new MockHttpSession();
    String before = pre.getId();
    MvcResult r =
        mvc.perform(
                post("/api/v1/screens/cosgn00")
                    .session(pre)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("ENTER", "{\"USERID\":\"USER0004\",\"PASSWD\":\"PASSWORD\"}")))
            .andExpect(jsonPath("$.nextScreen").value("COMEN01"))
            .andReturn();
    assertThat(r.getRequest().getSession(false).getId()).isNotEqualTo(before);
  }

  @Test
  void failedSignOnDoesNotAuthenticate() throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/v1/screens/cosgn00")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("ENTER", "{\"USERID\":\"USER0001\",\"PASSWD\":\"NOPE\"}")))
            .andExpect(jsonPath("$.messages[0].text").value("Wrong Password. Try again ..."))
            .andExpect(jsonPath("$.data.PASSWD").doesNotExist())
            .andReturn();
    assertThat(r.getRequest().getSession(false)).isNull();
    mvc.perform(get("/api/v1/screens/comen01").with(httpBasic("USER0001", "NOPE")))
        .andExpect(status().isUnauthorized());
    verify(users, never()).save(any());
  }

  @Test
  void contextIdentityComesFromThePrincipalNotFromTheSession() throws Exception {
    MockHttpSession s = signOn("USER0001");
    // a tampered COMMAREA claiming another user/admin type is ignored
    s.setAttribute(
        ConversationStore.ATTRIBUTE,
        com.carddemo.contracts.screen.ConversationContext.signedOn(
                "ADMIN001", com.carddemo.contracts.screen.ConversationContext.UserType.ADMIN)
            .reenter());
    mvc.perform(get("/api/v1/screens/comen01").session(s))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.context.userId").value("USER0001"))
        .andExpect(jsonPath("$.context.userType").value("USER"));
    s.setAttribute(ConversationStore.ATTRIBUTE, "legacy JSON payload");
    mvc.perform(get("/api/v1/screens/comen01").session(s))
        .andExpect(jsonPath("$.context.userId").value("USER0001"));
    mvc.perform(get("/api/v1/screens/coadm01").session(s)).andExpect(status().isForbidden());
  }

  @Test
  void httpBasicClientsAreStateless() throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/v1/screens/comen01")
                    .with(httpBasic("user0005", "password"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("ENTER", "{\"OPTION\":\"10\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextScreen").value("COBIL00"))
            .andExpect(jsonPath("$.context.userId").value("USER0005"))
            .andReturn();
    assertThat(r.getRequest().getSession(false)).isNull();
  }

  @Test
  void crossSiteFormPostsCannotSignOn() throws Exception {
    mvc.perform(
            post("/api/v1/screens/cosgn00")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("action=ENTER&USERID=USER0001&PASSWD=PASSWORD"))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            post("/api/v1/screens/cosgn00")
                .contentType(MediaType.TEXT_PLAIN)
                .content(body("ENTER", "{\"USERID\":\"USER0001\",\"PASSWD\":\"PASSWORD\"}")))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void cookieSessionPostsRequireTheCsrfTokenBasicClientsDoNot() throws Exception {
    MockHttpSession s = signOn("USER0001");
    MockMvc raw = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    raw.perform(
            post("/api/v1/screens/comen01")
                .session(s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ENTER", "{\"OPTION\":\"1\"}")))
        .andExpect(status().isForbidden());
    raw.perform(
            post("/api/v1/screens/cosgn00")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ENTER", "{\"USERID\":\"USER0001\",\"PASSWD\":\"PASSWORD\"}")))
        .andExpect(status().isForbidden());
    raw.perform(
            post("/api/v1/screens/comen01")
                .with(httpBasic("user0001", "password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ENTER", "{\"OPTION\":\"10\"}")))
        .andExpect(status().isOk());
  }

  @Test
  void missingActionIsABadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/screens/cosgn00")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":{}}"))
        .andExpect(status().isBadRequest());
  }
}
