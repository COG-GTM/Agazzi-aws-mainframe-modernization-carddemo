package com.carddemo.online.account.parity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/**
 * Runs the O2 online goldens (format of {@code com.carddemo.parity.online.OnlineScenario}) through
 * MockMvc with one HTTP session per scenario. Extra expectation {@code data: {FIELD: value}} checks
 * map output fields.
 */
public final class GoldenScenarios {

  private GoldenScenarios() {}

  public static Path dir(String tranid) {
    Path root = Path.of(System.getProperty("modern.root", "..")).toAbsolutePath().normalize();
    return root.resolve("carddemo-parity/src/test/resources/golden/o2-account/online/" + tranid);
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> load(String tranid) throws IOException {
    List<Map<String, Object>> out = new ArrayList<>();
    try (Stream<Path> s = Files.list(dir(tranid))) {
      for (Path p : s.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
          out.add((Map<String, Object>) new Yaml().load(r));
        }
      }
    }
    return out;
  }

  /** Returns the mismatches of one scenario (empty = parity). */
  @SuppressWarnings("unchecked")
  public static List<String> run(MockMvc mvc, Map<String, Object> sc, Runnable dbCheck)
      throws Exception {
    ObjectMapper json = new ObjectMapper();
    MockHttpSession session = new MockHttpSession();
    Map<String, Object> auth =
        (Map<String, Object>)
            sc.getOrDefault("auth", Map.of("user", "USER0001", "password", "PASSWORD"));
    String screen = String.valueOf(sc.get("screen"));
    List<String> mismatches = new ArrayList<>();
    int i = 0;
    for (Map<String, Object> step : (List<Map<String, Object>>) sc.get("steps")) {
      i++;
      String where = sc.get("id") + " step " + i;
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("action", step.getOrDefault("action", "ENTER"));
      body.put("fields", step.getOrDefault("fields", Map.of()));
      String resp =
          mvc.perform(
                  post("/api/v1/screens/" + screen.toLowerCase(Locale.ROOT))
                      .session(session)
                      .with(
                          httpBasic(
                              String.valueOf(auth.get("user")),
                              String.valueOf(auth.get("password"))))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json.writeValueAsString(body)))
              .andReturn()
              .getResponse()
              .getContentAsString();
      JsonNode r = json.readTree(resp);
      Map<String, Object> e = (Map<String, Object>) step.getOrDefault("expect", Map.of());
      if (e.containsKey("nextScreen")
          && !Objects.equals(
              e.get("nextScreen") == null ? null : String.valueOf(e.get("nextScreen")),
              text(r.get("nextScreen")))) {
        mismatches.add(
            where + ": nextScreen " + e.get("nextScreen") + " got " + text(r.get("nextScreen")));
      }
      if (e.get("message") != null) {
        List<String> msgs = new ArrayList<>();
        r.path("messages").forEach(m -> msgs.add(m.path("text").asText().strip()));
        if (!msgs.contains(String.valueOf(e.get("message")).strip())) {
          mismatches.add(where + ": message [" + e.get("message") + "] got " + msgs);
        }
      }
      if (e.get("fieldErrors") != null) {
        List<String> fields = new ArrayList<>();
        r.path("fieldErrors").forEach(f -> fields.add(f.path("field").asText()));
        if (!fields.equals(e.get("fieldErrors"))) {
          mismatches.add(where + ": fieldErrors " + e.get("fieldErrors") + " got " + fields);
        }
      }
      if (e.get("data") instanceof Map<?, ?> data) {
        data.forEach(
            (k, v) -> {
              String got = text(r.path("data").get(String.valueOf(k)));
              if (!Objects.equals(v == null ? null : String.valueOf(v), got)) {
                mismatches.add(where + ": data." + k + " [" + v + "] got [" + got + "]");
              }
            });
      }
      if (text(r.get("nextScreen")) != null) {
        screen = text(r.get("nextScreen"));
      }
    }
    return mismatches;
  }

  private static String text(JsonNode n) {
    return n == null || n.isNull() ? null : n.asText();
  }
}
