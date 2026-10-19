package com.carddemo.online.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.online.useradmin.session.UseradminSession;
import com.carddemo.online.useradmin.session.UseradminState;
import com.carddemo.online.useradmin.support.InMemoryUserStore;
import com.carddemo.online.useradmin.support.UseradminTestApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = UseradminTestApp.class)
@AutoConfigureMockMvc
@DirtiesContext
class UserAdminScreenControllerTest {

  @Autowired MockMvc mvc;
  @Autowired com.carddemo.online.useradmin.store.UserStore store;

  private static final String[] SCREENS = {"cousr00", "cousr01", "cousr02", "cousr03"};

  @ParameterizedTest
  @ValueSource(strings = {"cousr00", "cousr01", "cousr02", "cousr03"})
  void anonymousIsUnauthorized(String screen) throws Exception {
    mvc.perform(get("/api/v1/screens/" + screen)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"USER0001", "USER0002"})
  void regularUserIsForbiddenOnEveryScreen(String user) throws Exception {
    for (String s : SCREENS) {
      mvc.perform(get("/api/v1/screens/" + s).with(httpBasic(user, "PASSWORD")))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/api/v1/screens/" + s)
                  .with(httpBasic(user, "PASSWORD"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"action\":\"ENTER\",\"fields\":{\"USRIDIN\":\"ADMIN001\"}}"))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void adminFromRoleOrFromUsrsecTypeIsAllowed() throws Exception {
    mvc.perform(get("/api/v1/screens/cousr00").with(httpBasic("ADMIN001", "PASSWORD")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.screen").value("COUSR00"))
        .andExpect(jsonPath("$.data.USRID01").value("ADMIN001"))
        .andExpect(jsonPath("$.data.PAGENUM").value("00000001"))
        .andExpect(
            jsonPath("$.messages[0].text").value("You have reached the bottom of the page..."))
        .andExpect(jsonPath("$.context.userType").value("ADMIN"))
        .andExpect(jsonPath("$.context.programContext").value("REENTER"));
    mvc.perform(get("/api/v1/screens/cousr01").with(httpBasic("ADMIN002", "PASSWORD")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.TRNNAME").value("CU01"));
  }

  @Test
  void sessionCarriesSelectionFromListToUpdateAndNeverReturnsPasswords() throws Exception {
    MockHttpSession session = new MockHttpSession();
    String admin = "ADMIN001";
    String list =
        mvc.perform(
                get("/api/v1/screens/cousr00").session(session).with(httpBasic(admin, "PASSWORD")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(list).doesNotContain("PASSWORD\"").doesNotContain("bcrypt");
    mvc.perform(
            post("/api/v1/screens/cousr00")
                .session(session)
                .with(httpBasic(admin, "PASSWORD"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ENTER\",\"fields\":{\"SEL0006\":\"U\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextScreen").value("COUSR02"))
        .andExpect(jsonPath("$.context.fromProgram").value("COUSR00C"));
    String upd =
        mvc.perform(
                get("/api/v1/screens/cousr02").session(session).with(httpBasic(admin, "PASSWORD")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.USRIDIN").value("USER0001"))
            .andExpect(jsonPath("$.data.PASSWD").value(""))
            .andExpect(
                jsonPath("$.messages[0].text").value("Press PF5 key to save your updates ..."))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(upd).doesNotContain("PASSWORD\"").doesNotContain("bcrypt");
    mvc.perform(
            post("/api/v1/screens/cousr02")
                .session(session)
                .with(httpBasic(admin, "PASSWORD"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"action\":\"PF5\",\"fields\":{\"USRIDIN\":\"USER0001\",\"FNAME\":\"USER\","
                        + "\"LNAME\":\"0001\",\"PASSWD\":\"NEWPASS1\",\"USRTYPE\":\"U\"}}"))
        .andExpect(jsonPath("$.messages[0].text").value("User USER0001 has been updated ..."))
        .andExpect(jsonPath("$.data.PASSWD").value(""));
    mvc.perform(
            post("/api/v1/screens/cousr02")
                .session(session)
                .with(httpBasic(admin, "PASSWORD"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PF3\",\"fields\":{}}"))
        .andExpect(jsonPath("$.nextScreen").value("COUSR00"))
        .andExpect(jsonPath("$.messages[0].text").value("User ID can NOT be empty..."));
    mvc.perform(
            post("/api/v1/screens/cousr03")
                .session(session)
                .with(httpBasic(admin, "PASSWORD"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ENTER\",\"fields\":{\"USRIDIN\":\"USER0001\"}}"))
        .andExpect(jsonPath("$.messages[0].text").value("Press PF5 key to delete this user ..."));
    mvc.perform(
            post("/api/v1/screens/cousr01")
                .session(session)
                .with(httpBasic(admin, "PASSWORD"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PF3\"}"))
        .andExpect(jsonPath("$.nextScreen").value("COADM01"));
  }

  @Test
  void demotedAdministratorLosesAccessAndUserSwitchDropsState() throws Exception {
    InMemoryUserStore users = (InMemoryUserStore) store;
    MockHttpSession session = new MockHttpSession();
    mvc.perform(
            get("/api/v1/screens/cousr00").session(session).with(httpBasic("ADMIN002", "PASSWORD")))
        .andExpect(status().isOk());
    users.put("ADMIN002", "ADMIN", "002", "PASSWORD", "U");
    mvc.perform(
            get("/api/v1/screens/cousr00").session(session).with(httpBasic("ADMIN002", "PASSWORD")))
        .andExpect(status().isForbidden());
    users.reseed(0);

    UseradminState foreign = new UseradminState();
    foreign.selected("USER0003");
    session.setAttribute(UseradminSession.STATE_ATTRIBUTE, foreign);
    session.setAttribute(
        UseradminSession.CONTEXT_ATTRIBUTE,
        ConversationContext.signedOn("ADMIN002", UserType.ADMIN));
    mvc.perform(
            get("/api/v1/screens/cousr03").session(session).with(httpBasic("ADMIN001", "PASSWORD")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.USRIDIN").value(""))
        .andExpect(jsonPath("$.messages").isEmpty());
  }

  @Test
  void staleSessionContextOfAnotherUserIsIgnored() throws Exception {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(
        UseradminSession.CONTEXT_ATTRIBUTE,
        ConversationContext.signedOn("ADMIN009", UserType.ADMIN));
    mvc.perform(
            get("/api/v1/screens/cousr00").session(session).with(httpBasic("USER0001", "PASSWORD")))
        .andExpect(status().isForbidden());
    session.setAttribute(
        UseradminSession.CONTEXT_ATTRIBUTE,
        ConversationContext.signedOn("USER0002", UserType.ADMIN));
    mvc.perform(
            get("/api/v1/screens/cousr03").session(session).with(httpBasic("USER0002", "PASSWORD")))
        .andExpect(status().isForbidden());
  }
}
