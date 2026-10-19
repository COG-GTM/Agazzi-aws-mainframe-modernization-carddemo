package com.carddemo.online.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.online.card.repository.CardRewriter;
import com.carddemo.online.card.service.CardDetailService;
import com.carddemo.online.card.service.CardFixtures;
import com.carddemo.online.card.service.CardListService;
import com.carddemo.online.card.service.CardUpdateService;
import com.carddemo.online.card.session.CardConversation;
import com.carddemo.online.card.session.ContextEdit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CardScreensControllerTest {

  final CardFixtures fx = new CardFixtures(20);
  final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(
              new CardScreensController(
                  new CardConversation(),
                  new CardListService(fx.browser, fx.clock),
                  new CardDetailService(fx.repo, fx.clock),
                  new CardUpdateService(fx.repo, new CardRewriter(fx.repo), fx.clock)))
          .build();
  final MockHttpSession session = new MockHttpSession();

  static UsernamePasswordAuthenticationToken auth(String user, String role) {
    return new UsernamePasswordAuthenticationToken(
        user, "x", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  ResultActions send(String map, String body, String role) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post("/api/v1/screens/" + map)
            .session(session)
            .principal(auth(role + "001", role))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void listGetThenPostKeepsSessionState() throws Exception {
    mvc.perform(
            get("/api/v1/screens/cocrdli").session(session).principal(auth("ADMIN001", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.screen").value("COCRDLI"))
        .andExpect(jsonPath("$.data.PAGENO").value("1"))
        .andExpect(jsonPath("$.data.CRDNUM1").value(CardFixtures.num(1)))
        .andExpect(jsonPath("$.context.userType").value("ADMIN"));
    send("cocrdli", "{\"action\":\"PF8\",\"fields\":{}}", "ADMIN")
        .andExpect(jsonPath("$.data.PAGENO").value("2"))
        .andExpect(jsonPath("$.data.CRDNUM1").value(CardFixtures.num(8)));
    send("cocrdli", "{\"action\":\"ENTER\",\"fields\":{\"CRDSEL2\":\"S\"}}", "ADMIN")
        .andExpect(jsonPath("$.nextScreen").value("COCRDSL"));
    send("cocrdsl", "{\"action\":\"ENTER\",\"fields\":{}}", "ADMIN")
        .andExpect(jsonPath("$.data.CARDSID").value(CardFixtures.num(9)));
    assertThat(session.getAttribute(CardConversation.CONTEXT_ATTRIBUTE))
        .isInstanceOf(ConversationContext.class);
  }

  @Test
  void detailAndUpdateEndpoints() throws Exception {
    mvc.perform(
            get("/api/v1/screens/cocrdsl").session(session).principal(auth("ADMIN001", "ADMIN")))
        .andExpect(jsonPath("$.screen").value("COCRDSL"))
        .andExpect(jsonPath("$.messages[0].text").value(CardDetailService.MSG_PROMPT));
    mvc.perform(
            get("/api/v1/screens/cocrdup").session(session).principal(auth("ADMIN001", "ADMIN")))
        .andExpect(jsonPath("$.screen").value("COCRDUP"));
    String acct = CardFixtures.acctText(4);
    send(
            "cocrdup",
            "{\"action\":\"ENTER\",\"fields\":{\"ACCTSID\":\""
                + acct
                + "\",\"CARDSID\":\""
                + CardFixtures.num(4)
                + "\"}}",
            "ADMIN")
        .andExpect(jsonPath("$.data.CRDNAME").value("HOLDER NAME"));
    String edit =
        "{\"CRDNAME\":\"NEW ONE\",\"CRDSTCD\":\"N\",\"EXPMON\":\"2\",\"EXPYEAR\":\"2030\"}";
    send("cocrdup", "{\"action\":\"ENTER\",\"fields\":" + edit + "}", "ADMIN")
        .andExpect(jsonPath("$.messages[0].text").value(CardUpdateService.INFO_CONFIRM));
    send("cocrdup", "{\"action\":\"PF5\",\"fields\":" + edit + "}", "ADMIN")
        .andExpect(jsonPath("$.messages[0].text").value(CardUpdateService.INFO_DONE));
    assertThat(fx.file.get(CardFixtures.num(4)).getCardActiveStatus()).isEqualTo("N");
  }

  @Test
  void regularUserIsConfinedToTheCommareaAccount() throws Exception {
    ConversationContext menu =
        ContextEdit.of(ConversationContext.signedOn("USER001", ConversationContext.UserType.USER))
            .from("CM00", "COMEN01C")
            .account(CardFixtures.acct(2))
            .build();
    session.setAttribute(CardConversation.CONTEXT_ATTRIBUTE, menu);
    send("cocrdli", "{\"action\":\"ENTER\",\"fields\":{}}", "USER")
        .andExpect(jsonPath("$.data.CRDNUM1").value(CardFixtures.num(2)))
        .andExpect(jsonPath("$.data.CRDNUM2").value(CardFixtures.num(5)));
    send(
            "cocrdli",
            "{\"action\":\"ENTER\",\"fields\":{\"ACCTSID\":\"" + CardFixtures.acctText(1) + "\"}}",
            "USER")
        .andExpect(jsonPath("$.messages[0].text").value(CardListService.MSG_NO_RECORDS));
    send(
            "cocrdsl",
            "{\"action\":\"ENTER\",\"fields\":{\"ACCTSID\":\""
                + CardFixtures.acctText(1)
                + "\",\"CARDSID\":\""
                + CardFixtures.num(1)
                + "\"}}",
            "USER")
        .andExpect(jsonPath("$.messages[0].text").value(CardDetailService.MSG_NOT_FOUND));
  }
}
