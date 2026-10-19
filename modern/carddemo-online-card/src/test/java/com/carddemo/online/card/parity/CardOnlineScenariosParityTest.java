package com.carddemo.online.card.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.carddemo.online.card.support.PostgresTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

/**
 * Runs the o3-card online goldens ({@code golden/o3-card/online/<TRANID>/*.yaml}, format of {@code
 * com.carddemo.parity.online.OnlineScenario}) against the card screens on PostgreSQL + seed V2. One
 * HTTP session per scenario; every scenario is rolled back so they stay independent.
 */
@Tag("parity")
class CardOnlineScenariosParityTest extends PostgresTestBase {

  static final Path GOLDEN =
      Path.of(System.getProperty("modern.root", ".."))
          .resolve("carddemo-parity/src/test/resources/golden/o3-card/online");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager tx;
  final ObjectMapper json = new ObjectMapper();

  @TestFactory
  Stream<DynamicTest> o3CardScenarios() throws IOException {
    List<Path> files;
    try (Stream<Path> s = Files.walk(GOLDEN)) {
      files = s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
    }
    assertThat(files).isNotEmpty();
    return files.stream()
        .map(f -> DynamicTest.dynamicTest(GOLDEN.relativize(f).toString(), () -> runFile(f)));
  }

  @SuppressWarnings("unchecked")
  void runFile(Path file) throws IOException {
    Map<String, Object> sc;
    try (InputStream in = Files.newInputStream(file)) {
      sc = new Yaml().load(in);
    }
    List<String> mismatches = new ArrayList<>();
    TransactionTemplate tt = new TransactionTemplate(tx);
    tt.executeWithoutResult(
        status -> {
          status.setRollbackOnly();
          try {
            mismatches.addAll(run(sc));
          } catch (Exception e) {
            mismatches.add(sc.get("id") + ": " + e);
          }
        });
    assertThat(mismatches).as("scenario %s", sc.get("id")).isEmpty();
  }

  @SuppressWarnings("unchecked")
  List<String> run(Map<String, Object> sc) throws Exception {
    List<String> out = new ArrayList<>();
    Map<String, String> auth = (Map<String, String>) sc.get("auth");
    String user = auth == null ? "ADMIN001" : auth.get("user");
    String pwd = auth == null ? "PASSWORD" : auth.get("password");
    MockHttpSession session = new MockHttpSession();
    String current = (String) sc.get("screen");
    List<Map<String, Object>> steps = (List<Map<String, Object>>) sc.get("steps");
    for (int i = 0; i < steps.size(); i++) {
      Map<String, Object> step = steps.get(i);
      Map<String, Object> expect = (Map<String, Object>) step.get("expect");
      String where =
          "%s step %d (%s on %s)".formatted(sc.get("id"), i + 1, step.get("action"), current);
      String before = cardTable();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("action", step.get("action"));
      body.put("fields", step.get("fields") == null ? Map.of() : step.get("fields"));
      String resp =
          mvc.perform(
                  post("/api/v1/screens/" + current.toLowerCase(Locale.ROOT))
                      .session(session)
                      .with(httpBasic(user, pwd))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json.writeValueAsString(body)))
              .andReturn()
              .getResponse()
              .getContentAsString();
      JsonNode r = json.readTree(resp);
      out.addAll(compare(where, expect, r));
      if ("none".equals(expect.get("dbDelta")) && !before.equals(cardTable())) {
        out.add(where + ": expected no DB delta");
      }
      if ("changed".equals(expect.get("dbDelta")) && before.equals(cardTable())) {
        out.add(where + ": expected a DB delta");
      }
      JsonNode next = r.get("nextScreen");
      if (next != null && !next.isNull()) {
        current = next.asText();
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  static List<String> compare(String where, Map<String, Object> e, JsonNode r) {
    List<String> out = new ArrayList<>();
    if (e.get("screen") != null && !e.get("screen").equals(text(r.get("screen")))) {
      out.add(where + ": screen expected " + e.get("screen") + " got " + text(r.get("screen")));
    }
    if (e.containsKey("nextScreen")
        && !Objects.equals(e.get("nextScreen"), text(r.get("nextScreen")))) {
      out.add(
          where
              + ": nextScreen expected "
              + e.get("nextScreen")
              + " got "
              + text(r.get("nextScreen")));
    }
    if (e.get("message") != null) {
      List<String> msgs = new ArrayList<>();
      r.path("messages").forEach(m -> msgs.add(m.path("text").asText().strip()));
      if (!msgs.contains(((String) e.get("message")).strip())) {
        out.add(where + ": message expected [" + e.get("message") + "] got " + msgs);
      }
    }
    if (e.get("fieldErrors") != null) {
      List<String> fields = new ArrayList<>();
      r.path("fieldErrors").forEach(f -> fields.add(f.path("field").asText()));
      if (!fields.equals(e.get("fieldErrors"))) {
        out.add(where + ": fieldErrors expected " + e.get("fieldErrors") + " got " + fields);
      }
    }
    Map<String, Object> data = (Map<String, Object>) e.get("data");
    if (data != null) {
      data.forEach(
          (k, v) -> {
            String got = text(r.path("data").get(k));
            if (!Objects.equals(v == null ? null : String.valueOf(v), got)) {
              out.add(where + ": data." + k + " expected " + v + " got " + got);
            }
          });
    }
    return out;
  }

  private String cardTable() {
    return String.valueOf(
        jdbc.queryForObject(
            "select md5(string_agg(c::text, '|' order by card_num)) from card c", String.class));
  }

  private static String text(JsonNode n) {
    return n == null || n.isNull() ? null : n.asText();
  }
}
