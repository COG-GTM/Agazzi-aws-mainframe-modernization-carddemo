package com.carddemo.ext.authorization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.ext.authorization.MutableBusinessClock;
import com.carddemo.ext.authorization.X1Fixtures;
import com.carddemo.ext.authorization.codec.AuthorizationRequest;
import com.carddemo.ext.authorization.model.AuthFraud;
import com.carddemo.ext.authorization.service.AuthorizationDecisionService;
import com.carddemo.ext.authorization.store.AuthFraudRepository;
import com.carddemo.ext.authorization.store.PendingAuthDetailRepository;
import com.carddemo.ext.authorization.store.PendingAuthSummaryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "USER0001")
class AuthorizationScreensTest {

  static final String CARD = "4111111111111111";
  final ObjectMapper json = new ObjectMapper();

  @Autowired MockMvc mvc;
  @Autowired AccountRepository accounts;
  @Autowired CustomerRepository customers;
  @Autowired CardXrefRepository xrefs;
  @Autowired PendingAuthSummaryRepository summaries;
  @Autowired PendingAuthDetailRepository details;
  @Autowired AuthFraudRepository frauds;
  @Autowired AuthorizationDecisionService decisions;
  @Autowired MutableBusinessClock clock;
  @Autowired AuthViewStateStore states;

  @BeforeEach
  void seed() {
    frauds.deleteAll();
    details.deleteAll();
    summaries.deleteAll();
    xrefs.deleteAll();
    accounts.deleteAll();
    customers.deleteAll();
    accounts.save(X1Fixtures.account(1L, "5000.00", "0.00"));
    customers.save(X1Fixtures.customer(7));
    xrefs.save(X1Fixtures.xref(CARD, 7, 1L));
    for (int i = 0; i < 7; i++) {
      clock.set(LocalDateTime.of(2022, 7, 18, 10, i));
      decisions.process(
          AuthorizationRequest.parse(
              X1Fixtures.request(CARD, (i + 1) + "0.00", "TXN00000000000" + i)));
    }
    AuthViewState st = states.get("USER0001");
    st.acctId = null;
    st.pageNum = 0;
    st.last = null;
    st.nextPage = false;
    java.util.Arrays.fill(st.authKeys, null);
  }

  JsonNode send(String screen, String action, Map<String, String> fields) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("action", action);
    body.put("fields", fields);
    String r =
        mvc.perform(
                post("/api/v1/screens/" + screen)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(r);
  }

  static String msg(JsonNode r) {
    return r.path("messages").path(0).path("text").asText("");
  }

  @Test
  void cpvsValidatesAccountId() throws Exception {
    JsonNode r = send("copau00", "ENTER", Map.of("ACCTID", ""));
    assertThat(msg(r)).isEqualTo("Please enter Acct Id...");
    assertThat(r.path("fieldErrors").path(0).path("field").asText()).isEqualTo("ACCTID");
    assertThat(msg(send("copau00", "ENTER", Map.of("ACCTID", "12A"))))
        .isEqualTo("Acct Id must be Numeric ...");
    assertThat(msg(send("copau00", "PF2", Map.of()))).isEqualTo(ScreenFormat.INVALID_KEY);
    assertThat(send("copau00", "PF3", Map.of()).path("nextScreen").asText()).isEqualTo("COMEN01");
  }

  @Test
  void cpvsPagesFiveRowsNewestFirst() throws Exception {
    JsonNode p1 = send("copau00", "ENTER", Map.of("ACCTID", "00000000001"));
    JsonNode d = p1.path("data");
    assertThat(d.path("TRNNAME").asText()).isEqualTo("CPVS");
    assertThat(d.path("CNAME").asText()).isEqualTo("Immanuel M Kessler");
    assertThat(d.path("APPRCNT").asText()).isEqualTo("007");
    assertThat(d.path("TRNID01").asText()).isEqualTo("TXN000000000006");
    assertThat(d.path("TRNID05").asText()).isEqualTo("TXN000000000002");
    assertThat(d.path("PAPRV01").asText()).isEqualTo("A");
    assertThat(d.path("PDATE01").asText()).isEqualTo("07/18/22");
    assertThat(d.path("PAMT001").asText()).isEqualTo("       70.00");

    JsonNode p2 = send("copau00", "PF8", Map.of("ACCTID", "00000000001"));
    assertThat(p2.path("data").path("TRNID01").asText()).isEqualTo("TXN000000000001");
    assertThat(p2.path("data").path("TRNID02").asText()).isEqualTo("TXN000000000000");
    assertThat(p2.path("data").path("TRNID03").asText()).isEmpty();
    // COPAUS0C quirk: a short last page leaves NEXT-PAGE-FLG at 'Y', so PF8 shows an empty page.
    JsonNode p3 = send("copau00", "PF8", Map.of());
    assertThat(msg(p3)).isEmpty();
    assertThat(p3.path("data").path("TRNID01").asText()).isEmpty();
    JsonNode back = send("copau00", "PF7", Map.of());
    assertThat(back.path("data").path("TRNID01").asText()).isEqualTo("TXN000000000006");
    // PROCESS-PAGE-FORWARD re-increments CDEMO-CPVS-PAGE-NUM, so PF7 keeps showing page 1.
    assertThat(msg(send("copau00", "PF7", Map.of()))).isEmpty();
    send("copau00", "ENTER", Map.of("ACCTID", "00000000001"));
    assertThat(msg(send("copau00", "PF7", Map.of()))).isEqualTo(CpvsScreenService.MSG_TOP);
    assertThat(msg(send("copau00", "ENTER", Map.of("ACCTID", "00000000001", "SEL0002", "X"))))
        .isEqualTo(CpvsScreenService.MSG_INVALID_SEL);
  }

  @Test
  void cpvdShowsDetailTogglesFraudAndPages() throws Exception {
    send("copau00", "ENTER", Map.of("ACCTID", "00000000001"));
    JsonNode sel = send("copau00", "ENTER", Map.of("ACCTID", "00000000001", "SEL0001", "s"));
    assertThat(sel.path("nextScreen").asText()).isEqualTo("COPAU01");

    String first =
        mvc.perform(get("/api/v1/screens/copau01"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode d = json.readTree(first).path("data");
    assertThat(d.path("CARDNUM").asText()).isEqualTo(CARD);
    assertThat(d.path("TRNID").asText()).isEqualTo("TXN000000000006");
    assertThat(d.path("AUTHRSN").asText()).isEqualTo("0000-APPROVED");
    assertThat(d.path("AUTHFRD").asText()).isEqualTo("-");
    assertThat(d.path("CRDEXP").asText()).isEqualTo("12/25");

    JsonNode marked = send("copau01", "PF5", Map.of());
    assertThat(msg(marked)).isEqualTo("AUTH MARKED FRAUD...");
    assertThat(marked.path("data").path("AUTHFRD").asText()).isEqualTo("F-07/18/22");
    AuthFraud row = frauds.findAll().get(0);
    assertThat(row.getAuthFraud()).isEqualTo("F");
    assertThat(row.getCardNum()).isEqualTo(CARD);
    assertThat(row.getAuthTs()).isEqualTo(LocalDateTime.of(2022, 7, 18, 10, 6));

    JsonNode removed = send("copau01", "PF5", Map.of());
    assertThat(msg(removed)).isEqualTo("AUTH FRAUD REMOVED...");
    assertThat(frauds.findAll())
        .hasSize(1)
        .first()
        .extracting(AuthFraud::getAuthFraud)
        .isEqualTo("R");

    JsonNode next = send("copau01", "PF8", Map.of());
    assertThat(next.path("data").path("TRNID").asText()).isEqualTo("TXN000000000005");
    assertThat(send("copau01", "PF3", Map.of()).path("nextScreen").asText()).isEqualTo("COPAU00");
  }
}
