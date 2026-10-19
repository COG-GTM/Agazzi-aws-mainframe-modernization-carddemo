package com.carddemo.online.useradmin.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.carddemo.online.useradmin.service.PasswordMatcher;
import com.carddemo.online.useradmin.store.UserRow;
import com.carddemo.online.useradmin.store.UserStore;
import com.carddemo.online.useradmin.support.InMemoryUserStore;
import com.carddemo.online.useradmin.support.UseradminTestApp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.yaml.snakeyaml.Yaml;

/**
 * Golden online scenarios of CU00-CU03 ({@code golden/o5-useradmin/online/<TRANID>/*.yaml}),
 * replayed against the REST resources with the V2 seed and the golden fixed clock.
 *
 * <p>Format = {@code golden/online} (OnlineScenarioRunner) plus optional keys: {@code user}
 * (signed-on user, default ADMIN001), {@code extraUsers} (seeded U0000001..), per step {@code
 * screen} (map of the step, default the scenario one), {@code method: GET} (first send), and in
 * {@code expect}: {@code status}, {@code data} (map fields), {@code db} (USRSEC rows after the
 * step; {@code null} = absent).
 */
@Tag("parity")
@SpringBootTest(classes = UseradminTestApp.class)
@AutoConfigureMockMvc
@DirtiesContext
class UseradminGoldenScenariosTest {

  static final Path ROOT =
      Path.of(
          "..", "carddemo-parity", "src", "test", "resources", "golden", "o5-useradmin", "online");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserStore store;

  private final PasswordMatcher passwords = new PasswordMatcher();

  static List<Path> scenarios() throws IOException {
    try (Stream<Path> s = Files.walk(ROOT)) {
      return s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
    }
  }

  @Test
  void everyProgramHasGoldenScenarios() throws IOException {
    for (String tran : List.of("CU00", "CU01", "CU02", "CU03")) {
      assertThat(scenarios()).anyMatch(p -> p.getParent().getFileName().toString().equals(tran));
    }
  }

  @TestFactory
  Stream<DynamicTest> goldenScenarios() throws IOException {
    return scenarios().stream()
        .map(p -> DynamicTest.dynamicTest(ROOT.relativize(p).toString(), () -> replay(p)));
  }

  @SuppressWarnings("unchecked")
  private void replay(Path file) throws Exception {
    Map<String, Object> sc;
    try (InputStream in = Files.newInputStream(file)) {
      sc = new Yaml().load(in);
    }
    assertThat(sc).containsKeys("id", "tranid", "screen", "steps");
    assertThat(file.getParent().getFileName().toString()).isEqualTo(sc.get("tranid"));
    ((InMemoryUserStore) store).reseed(((Number) sc.getOrDefault("extraUsers", 0)).intValue());
    String user = (String) sc.getOrDefault("user", "ADMIN001");
    MockHttpSession session = new MockHttpSession();
    int n = 0;
    for (Map<String, Object> step : (List<Map<String, Object>>) sc.get("steps")) {
      n++;
      String where = sc.get("id") + " step " + n;
      String screen = ((String) step.getOrDefault("screen", sc.get("screen"))).toLowerCase();
      MockHttpServletRequestBuilder req;
      if ("GET".equals(step.get("method"))) {
        req = get("/api/v1/screens/" + screen);
      } else {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", step.get("action"));
        body.put("fields", step.getOrDefault("fields", Map.of()));
        req =
            post("/api/v1/screens/" + screen)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
      }
      Map<String, UserRow> before = ((InMemoryUserStore) store).snapshot();
      MockHttpServletResponse res =
          mvc.perform(req.session(session).with(httpBasic(user, "PASSWORD")))
              .andReturn()
              .getResponse();
      Map<String, Object> exp = (Map<String, Object>) step.getOrDefault("expect", Map.of());
      int status = ((Number) exp.getOrDefault("status", 200)).intValue();
      assertThat(res.getStatus()).as(where + " status").isEqualTo(status);
      if (status != 200) {
        continue;
      }
      String raw = res.getContentAsString();
      assertThat(raw)
          .as(where + " no password")
          .doesNotContain("{bcrypt}")
          .doesNotContain("\"PASSWORD\"");
      JsonNode node = json.readTree(raw);
      verify(where, exp, node, before);
    }
  }

  @SuppressWarnings("unchecked")
  private void verify(
      String where, Map<String, Object> exp, JsonNode node, Map<String, UserRow> before) {
    if (exp.containsKey("screen")) {
      assertThat(node.get("screen").asText()).as(where + " screen").isEqualTo(exp.get("screen"));
    }
    if (exp.containsKey("nextScreen")) {
      JsonNode next = node.get("nextScreen");
      Object e = exp.get("nextScreen");
      assertThat(next == null || next.isNull() ? null : next.asText())
          .as(where + " nextScreen")
          .isEqualTo(e);
    }
    if (exp.containsKey("message")) {
      JsonNode msgs = node.get("messages");
      Object e = exp.get("message");
      String actual = msgs.isEmpty() ? null : msgs.get(0).get("text").asText();
      assertThat(actual).as(where + " message").isEqualTo(e);
    }
    if (exp.containsKey("fieldErrors")) {
      List<String> actual = new ArrayList<>();
      node.get("fieldErrors").forEach(f -> actual.add(f.get("field").asText()));
      assertThat(actual).as(where + " fieldErrors").isEqualTo(exp.get("fieldErrors"));
    }
    if (exp.containsKey("data")) {
      ((Map<String, Object>) exp.get("data"))
          .forEach(
              (k, v) ->
                  assertThat(node.get("data").get(k).asText())
                      .as(where + " data." + k)
                      .isEqualTo(String.valueOf(v)));
    }
    Map<String, UserRow> db = ((InMemoryUserStore) store).snapshot();
    if ("none".equals(exp.get("dbDelta"))) {
      assertThat(db).as(where + " dbDelta").isEqualTo(before);
    }
    if (exp.containsKey("db")) {
      ((Map<String, Object>) exp.get("db"))
          .forEach((id, row) -> checkRow(where, db, id, (Map<String, Object>) row));
    }
  }

  private void checkRow(String where, Map<String, UserRow> db, String id, Map<String, Object> row) {
    UserRow actual = db.get(String.format("%-8s", id));
    if (row == null) {
      assertThat(actual).as(where + " db " + id + " absent").isNull();
      return;
    }
    assertThat(actual).as(where + " db " + id).isNotNull();
    row.forEach(
        (k, v) -> {
          switch (k) {
            case "fname" -> assertThat(actual.firstName().trim()).isEqualTo(v);
            case "lname" -> assertThat(actual.lastName().trim()).isEqualTo(v);
            case "type" -> assertThat(actual.type()).isEqualTo(v);
            case "password" ->
                assertThat(passwords.matches((String) v, actual.password()))
                    .as(where + " password")
                    .isTrue();
            case "bcrypt" ->
                assertThat(actual.password().startsWith("{bcrypt}"))
                    .as(where + " bcrypt")
                    .isEqualTo(v);
            default -> throw new AssertionError("unknown db key " + k);
          }
        });
  }
}
