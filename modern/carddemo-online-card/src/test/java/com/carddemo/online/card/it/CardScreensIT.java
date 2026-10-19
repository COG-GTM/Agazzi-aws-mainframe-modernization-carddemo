package com.carddemo.online.card.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.domain.entity.Card;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.online.card.repository.CardRewriter;
import com.carddemo.online.card.service.CardDetailService;
import com.carddemo.online.card.service.CardListService;
import com.carddemo.online.card.service.CardUpdateService;
import com.carddemo.online.card.session.CardConversation;
import com.carddemo.online.card.session.ContextEdit;
import com.carddemo.online.card.support.PostgresTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Authorization and concurrent REWRITE of CARDDAT on PostgreSQL 16 + seed V2. */
@Tag("it")
class CardScreensIT extends PostgresTestBase {

  static final String ACCT = "00000000002";
  static final String CARD = "0923877193247330";
  static final String OTHER_ACCT = "00000000050";
  static final String OTHER_CARD = "0500024453765740";

  @Autowired MockMvc mvc;
  @Autowired CardRepository cards;
  @Autowired CardRewriter rewriter;
  @Autowired PlatformTransactionManager tx;
  String originalName;

  @BeforeEach
  void remember() {
    originalName = cards.findById(CARD).orElseThrow().getCardEmbossedName();
  }

  @AfterEach
  void restore() {
    Card c = cards.findById(CARD).orElseThrow();
    c.setCardEmbossedName(originalName);
    cards.saveAndFlush(c);
  }

  ResultActions send(MockHttpSession s, String user, String map, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/screens/" + map)
            .session(s)
            .with(httpBasic(user, "PASSWORD"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  /** XCTL from the menu: the program sends its map first (ENTER context). */
  void firstSend(MockHttpSession s, String map) throws Exception {
    mvc.perform(get("/api/v1/screens/" + map).session(s).with(httpBasic("USER0001", "PASSWORD")))
        .andExpect(status().isOk());
  }

  static String keys(String acct, String card) {
    return "{\"action\":\"ENTER\",\"fields\":{\"ACCTSID\":\"%s\",\"CARDSID\":\"%s\"}}"
        .formatted(acct, card);
  }

  static MockHttpSession userSessionOnAccount(long acct) {
    MockHttpSession s = new MockHttpSession();
    ConversationContext menu =
        ContextEdit.of(ConversationContext.signedOn("USER0001", ConversationContext.UserType.USER))
            .from("CM00", "COMEN01C")
            .account(acct)
            .build();
    s.setAttribute(CardConversation.CONTEXT_ATTRIBUTE, menu);
    return s;
  }

  @Test
  void anonymousIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/screens/cocrdli").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void wrongPasswordIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/screens/cocrdli")
                .with(httpBasic("USER0001", "WRONG"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ENTER\",\"fields\":{}}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminSeesEveryAccount() throws Exception {
    MockHttpSession s = new MockHttpSession();
    send(s, "ADMIN001", "cocrdli", "{\"action\":\"ENTER\",\"fields\":{}}")
        .andExpect(jsonPath("$.data.CRDNUM1").value(OTHER_CARD))
        .andExpect(jsonPath("$.context.userType").value("ADMIN"));
    send(s, "ADMIN001", "cocrdsl", "{\"action\":\"ENTER\",\"fields\":{}}");
    send(new MockHttpSession(), "ADMIN001", "cocrdsl", keys(OTHER_ACCT, OTHER_CARD))
        .andExpect(jsonPath("$.messages[0].text").value(CardDetailService.MSG_FOUND));
  }

  @Test
  void userIsConfinedToOwnAccount() throws Exception {
    MockHttpSession s = userSessionOnAccount(2);
    send(s, "USER0001", "cocrdli", "{\"action\":\"ENTER\",\"fields\":{}}")
        .andExpect(jsonPath("$.data.CRDNUM1").value(CARD))
        .andExpect(jsonPath("$.data.CRDNUM2").doesNotExist());
    send(
            s,
            "USER0001",
            "cocrdli",
            "{\"action\":\"ENTER\",\"fields\":{\"ACCTSID\":\"" + OTHER_ACCT + "\"}}")
        .andExpect(jsonPath("$.messages[0].text").value(CardListService.MSG_NO_RECORDS));
    MockHttpSession d = userSessionOnAccount(2);
    firstSend(d, "cocrdsl");
    send(d, "USER0001", "cocrdsl", keys(OTHER_ACCT, OTHER_CARD))
        .andExpect(jsonPath("$.messages[0].text").value(CardDetailService.MSG_NOT_FOUND));
    send(d, "USER0001", "cocrdsl", keys(ACCT, CARD))
        .andExpect(jsonPath("$.messages[0].text").value(CardDetailService.MSG_FOUND));
    MockHttpSession u = userSessionOnAccount(2);
    firstSend(u, "cocrdup");
    send(u, "USER0001", "cocrdup", keys(OTHER_ACCT, OTHER_CARD))
        .andExpect(jsonPath("$.messages[0].text").value(CardUpdateService.MSG_NOT_FOUND));
  }

  @Test
  void secondUpdaterGetsRecordChangedBySomeoneElse() throws Exception {
    String edit1 =
        "{\"CRDNAME\":\"FIRST WRITER\",\"CRDSTCD\":\"Y\",\"EXPMON\":\"08\",\"EXPYEAR\":\"2024\"}";
    String edit2 =
        "{\"CRDNAME\":\"SECOND WRITER\",\"CRDSTCD\":\"Y\",\"EXPMON\":\"08\",\"EXPYEAR\":\"2024\"}";
    MockHttpSession a = new MockHttpSession();
    MockHttpSession b = new MockHttpSession();
    send(a, "ADMIN001", "cocrdup", keys(ACCT, CARD));
    send(b, "ADMIN001", "cocrdup", keys(ACCT, CARD));
    send(a, "ADMIN001", "cocrdup", "{\"action\":\"ENTER\",\"fields\":" + edit1 + "}");
    send(b, "ADMIN001", "cocrdup", "{\"action\":\"ENTER\",\"fields\":" + edit2 + "}");
    send(a, "ADMIN001", "cocrdup", "{\"action\":\"PF5\",\"fields\":" + edit1 + "}")
        .andExpect(jsonPath("$.messages[0].text").value(CardUpdateService.INFO_DONE));
    send(b, "ADMIN001", "cocrdup", "{\"action\":\"PF5\",\"fields\":" + edit2 + "}")
        .andExpect(jsonPath("$.messages[0].text").value(CardUpdateService.MSG_CHANGED))
        .andExpect(jsonPath("$.data.CRDNAME").value("FIRST WRITER"));
    assertThat(cards.findById(CARD).orElseThrow().getCardEmbossedName().strip())
        .isEqualTo("FIRST WRITER");
  }

  @Test
  void versionCheckRejectsAWriteThatSlipsBetweenReadAndRewrite() {
    TransactionTemplate other = new TransactionTemplate(tx);
    other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    Card values = cards.findById(CARD).orElseThrow();
    values.setCardEmbossedName(String.format("%-50s", "LOSER"));
    assertThatThrownBy(
            () ->
                rewriter.rewrite(
                    CARD,
                    current -> {
                      other.executeWithoutResult(
                          st -> {
                            Card c = cards.findById(CARD).orElseThrow();
                            c.setCardEmbossedName(String.format("%-50s", "WINNER"));
                            cards.saveAndFlush(c);
                          });
                      return true;
                    },
                    values))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(cards.findById(CARD).orElseThrow().getCardEmbossedName().strip())
        .isEqualTo("WINNER");
  }
}
