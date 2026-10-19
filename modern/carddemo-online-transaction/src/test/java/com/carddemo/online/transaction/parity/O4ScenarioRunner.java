package com.carddemo.online.transaction.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.Yaml;

/**
 * Runs the O4 online goldens ({@code golden/o4-transaction/online/<TRANID>/*.yaml}): the format of
 * {@code carddemo-parity} {@code OnlineScenario} (plan §11.4) plus the O4 keys {@code given.sql},
 * {@code signon}, step {@code get: true} (first send), {@code expect.fields} (map output subset)
 * and {@code expect.db} (SQL probes).
 */
final class O4ScenarioRunner {

  private final URI base;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  O4ScenarioRunner(URI base, JdbcTemplate jdbc) {
    this.base = base;
    this.jdbc = jdbc;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> load(Path file) throws Exception {
    try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return new Yaml().load(r);
    }
  }

  @SuppressWarnings("unchecked")
  List<String> run(Map<String, Object> sc) throws Exception {
    List<String> out = new ArrayList<>();
    Map<String, Object> given = (Map<String, Object>) sc.getOrDefault("given", Map.of());
    for (String sql : (List<String>) given.getOrDefault("sql", List.of())) {
      jdbc.execute(sql);
    }
    Map<String, Object> auth = (Map<String, Object>) sc.get("auth");
    String basic =
        auth == null
            ? null
            : "Basic "
                + Base64.getEncoder()
                    .encodeToString(
                        (auth.get("user") + ":" + auth.get("password"))
                            .getBytes(StandardCharsets.UTF_8));
    HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    String screen = String.valueOf(sc.get("screen")).toLowerCase(Locale.ROOT);
    if (sc.containsKey("signon")) {
      Map<String, Object> s = (Map<String, Object>) sc.get("signon");
      send(http, basic, "POST", "/test/signon", json.writeValueAsString(s));
    }
    List<Map<String, Object>> steps = (List<Map<String, Object>>) sc.get("steps");
    for (int i = 0; i < steps.size(); i++) {
      Map<String, Object> step = steps.get(i);
      String where = sc.get("id") + " step " + (i + 1);
      HttpResponse<String> resp;
      if (Boolean.TRUE.equals(step.get("get"))) {
        resp = send(http, basic, "GET", "/api/v1/screens/" + screen, null);
      } else {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", step.getOrDefault("action", "ENTER"));
        body.put("fields", step.getOrDefault("fields", Map.of()));
        resp =
            send(http, basic, "POST", "/api/v1/screens/" + screen, json.writeValueAsString(body));
      }
      Map<String, Object> e = (Map<String, Object>) step.getOrDefault("expect", Map.of());
      if (e.containsKey("status")) {
        if (!Objects.equals(((Number) e.get("status")).intValue(), resp.statusCode())) {
          out.add(where + ": HTTP " + resp.statusCode() + " expected " + e.get("status"));
        }
        continue;
      }
      if (resp.statusCode() != 200) {
        out.add(where + ": HTTP " + resp.statusCode() + " " + resp.body());
        break;
      }
      JsonNode r = json.readTree(resp.body());
      compare(where, e, r, out);
      JsonNode next = r.get("nextScreen");
      if (next != null && !next.isNull()) {
        screen = next.asText().toLowerCase(Locale.ROOT);
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private void compare(String where, Map<String, Object> e, JsonNode r, List<String> out) {
    if (e.containsKey("nextScreen")) {
      Object exp = e.get("nextScreen");
      String got = r.get("nextScreen").isNull() ? null : r.get("nextScreen").asText();
      if (!Objects.equals(exp == null ? null : String.valueOf(exp), got)) {
        out.add(where + ": nextScreen expected " + exp + " got " + got);
      }
    }
    if (e.containsKey("message")) {
      List<String> msgs = new ArrayList<>();
      r.path("messages").forEach(m -> msgs.add(m.path("text").asText().strip()));
      Object exp = e.get("message");
      if (exp == null ? !msgs.isEmpty() : !msgs.contains(String.valueOf(exp).strip())) {
        out.add(where + ": message expected [" + exp + "] got " + msgs);
      }
    }
    if (e.containsKey("severity")) {
      String got = r.path("messages").path(0).path("severity").asText();
      if (!got.equals(e.get("severity"))) {
        out.add(where + ": severity expected " + e.get("severity") + " got " + got);
      }
    }
    if (e.containsKey("fieldErrors")) {
      List<String> fields = new ArrayList<>();
      r.path("fieldErrors").forEach(f -> fields.add(f.path("field").asText()));
      if (!fields.equals(e.get("fieldErrors"))) {
        out.add(where + ": fieldErrors expected " + e.get("fieldErrors") + " got " + fields);
      }
    }
    Map<String, Object> fields = (Map<String, Object>) e.getOrDefault("fields", Map.of());
    fields.forEach(
        (k, v) -> {
          JsonNode n = r.path("data").get(k);
          String got = n == null || n.isNull() ? null : n.asText();
          String exp = v == null ? null : String.valueOf(v);
          if (!Objects.equals(exp, got) && !(exp != null && exp.isEmpty() && got == null)) {
            out.add(where + ": field " + k + " expected [" + exp + "] got [" + got + "]");
          }
        });
    for (Map<String, Object> probe :
        (List<Map<String, Object>>) e.getOrDefault("db", List.<Map<String, Object>>of())) {
      String got = jdbc.queryForObject(String.valueOf(probe.get("sql")), String.class);
      if (!Objects.equals(String.valueOf(probe.get("expect")), got)) {
        out.add(
            where
                + ": db ["
                + probe.get("sql")
                + "] expected "
                + probe.get("expect")
                + " got "
                + got);
      }
    }
  }

  private HttpResponse<String> send(
      HttpClient http, String basic, String method, String path, String body) throws Exception {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(base.resolve(path))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (basic != null) {
      b.header("Authorization", basic);
    }
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }
}
