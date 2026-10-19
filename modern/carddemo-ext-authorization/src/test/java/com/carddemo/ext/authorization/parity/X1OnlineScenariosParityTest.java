package com.carddemo.ext.authorization.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.ext.authorization.MutableBusinessClock;
import com.carddemo.ext.authorization.X1Fixtures;
import com.carddemo.ext.authorization.codec.AuthorizationRequest;
import com.carddemo.ext.authorization.service.AuthorizationDecisionService;
import com.carddemo.ext.authorization.store.AuthFraudRepository;
import com.carddemo.ext.authorization.store.PendingAuthDetailRepository;
import com.carddemo.ext.authorization.store.PendingAuthSummaryRepository;
import com.carddemo.ext.authorization.web.AuthViewStateStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/**
 * Runs golden/x1-authorization/online/{CPVS,CPVD}/*.yaml (OnlineScenario format of carddemo-parity)
 * against the X1 endpoints. Seed: account 1, card 4111111111111111, 7 approvals.
 */
@Tag("parity")
@SpringBootTest
@AutoConfigureMockMvc
class X1OnlineScenariosParityTest {

  static final Path ONLINE = X1BatchParityTest.GOLDEN.resolve("online");
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

  static Stream<Path> scenarios() throws IOException {
    try (Stream<Path> s = Files.walk(ONLINE)) {
      return s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList().stream();
    }
  }

  void seed() {
    frauds.deleteAll();
    details.deleteAll();
    summaries.deleteAll();
    xrefs.deleteAll();
    accounts.deleteAll();
    customers.deleteAll();
    accounts.save(X1Fixtures.account(1L, "5000.00", "0.00"));
    customers.save(X1Fixtures.customer(7));
    xrefs.save(X1Fixtures.xref("4111111111111111", 7, 1L));
    for (int i = 0; i < 7; i++) {
      clock.set(LocalDateTime.of(2022, 7, 18, 10, i));
      decisions.process(
          AuthorizationRequest.parse(
              X1Fixtures.request("4111111111111111", (i + 1) + "0.00", "TXN00000000000" + i)));
    }
  }

  @SuppressWarnings("unchecked")
  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  void scenarioMatchesCobol(Path file) throws Exception {
    Map<String, Object> sc;
    try (Reader r = Files.newBufferedReader(file)) {
      sc = new Yaml().load(r);
    }
    seed();
    String userId = ((Map<String, Object>) sc.get("auth")).get("user").toString();
    states.reset(userId);
    String current = sc.get("screen").toString();
    List<String> mismatches = new ArrayList<>();
    int i = 0;
    for (Map<String, Object> step : (List<Map<String, Object>>) sc.get("steps")) {
      i++;
      Map<String, Object> exp = (Map<String, Object>) step.get("expect");
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("action", step.get("action"));
      body.put("fields", step.getOrDefault("fields", Map.of()));
      long fraudRows = frauds.count();
      JsonNode resp =
          json.readTree(
              mvc.perform(
                      post("/api/v1/screens/" + current.toLowerCase(java.util.Locale.ROOT))
                          .with(user(userId))
                          .with(csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(json.writeValueAsString(body)))
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      String where = sc.get("id") + " step " + i + " " + step.get("action");
      if (exp.get("screen") != null && !exp.get("screen").equals(resp.path("screen").asText())) {
        mismatches.add(where + ": screen " + resp.path("screen").asText());
      }
      if (exp.containsKey("nextScreen")) {
        String want = exp.get("nextScreen") == null ? "" : exp.get("nextScreen").toString();
        String got = resp.path("nextScreen").isNull() ? "" : resp.path("nextScreen").asText();
        if (!want.equals(got)) {
          mismatches.add(where + ": nextScreen " + got + " != " + want);
        }
      }
      if (exp.containsKey("message")) {
        String got = resp.path("messages").path(0).path("text").asText("");
        if (!String.valueOf(exp.get("message")).equals(got)) {
          mismatches.add(where + ": message [" + got + "] != [" + exp.get("message") + "]");
        }
      }
      if (exp.containsKey("fieldErrors")) {
        List<String> got = new ArrayList<>();
        resp.path("fieldErrors").forEach(f -> got.add(f.path("field").asText()));
        if (!got.equals(exp.get("fieldErrors"))) {
          mismatches.add(where + ": fieldErrors " + got);
        }
      }
      if ("none".equals(exp.get("dbDelta")) && frauds.count() != fraudRows) {
        mismatches.add(where + ": unexpected AUTHFRDS delta");
      }
      String next = resp.path("nextScreen").isNull() ? "" : resp.path("nextScreen").asText();
      if (!next.isEmpty() && next.startsWith("COPAU")) {
        current = next;
      }
    }
    assertThat(mismatches).as(file.toString()).isEmpty();
  }
}
