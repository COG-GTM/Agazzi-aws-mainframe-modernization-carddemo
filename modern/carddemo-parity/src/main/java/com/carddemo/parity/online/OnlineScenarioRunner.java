package com.carddemo.parity.online;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.parity.db.DbSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Executes {@link OnlineScenario}s against {@code POST /api/v1/screens/<map>} (contracts-v1
 * OpenAPI) and compares message, next screen, field errors and DB delta with the expectation.
 */
public final class OnlineScenarioRunner {

  /** Outcome of one scenario: empty {@code mismatches} means parity. */
  public record Result(OnlineScenario scenario, List<String> mismatches) {
    public boolean passed() {
      return mismatches.isEmpty();
    }
  }

  private final URI baseUri;
  private final Callable<DbSnapshot> snapshots;
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private final ObjectMapper json = new ObjectMapper();

  /**
   * @param baseUri app root, e.g. {@code http://localhost:8080}
   * @param snapshots supplier of DB snapshots for {@code dbDelta} checks, or {@code null}
   */
  public OnlineScenarioRunner(URI baseUri, Callable<DbSnapshot> snapshots) {
    this.baseUri = baseUri;
    this.snapshots = snapshots;
  }

  public Result run(OnlineScenario sc) {
    List<String> mismatches = new ArrayList<>();
    // One terminal per scenario: its own session cookie and CSRF token, like a SPA tab.
    CookieManager cookies = new CookieManager();
    HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
    ScreenId current = sc.screen();
    for (int i = 0; i < sc.steps().size(); i++) {
      OnlineScenario.Step step = sc.steps().get(i);
      String where = "%s step %d (%s on %s)".formatted(sc.id(), i + 1, step.action(), current);
      try {
        DbSnapshot before = snapshot(step);
        JsonNode resp = post(http, cookies, sc, current, step);
        if (resp == null) {
          mismatches.add(where + ": endpoint not available");
          break;
        }
        mismatches.addAll(compare(where, step.expect(), resp));
        if (before != null) {
          List<DbSnapshot.RowChange> delta = before.diff(snapshots.call());
          if ("none".equalsIgnoreCase(step.expect().dbDelta()) && !delta.isEmpty()) {
            mismatches.add(where + ": expected no DB delta, got " + delta.size() + " changes");
          }
        }
        JsonNode next = resp.get("nextScreen");
        if (next != null && !next.isNull()) {
          current = ScreenId.valueOf(next.asText());
        }
      } catch (Exception e) {
        mismatches.add(where + ": " + e);
        break;
      }
    }
    return new Result(sc, List.copyOf(mismatches));
  }

  private DbSnapshot snapshot(OnlineScenario.Step step) throws Exception {
    return snapshots != null
            && step.expect().dbDelta() != null
            && !"any".equals(step.expect().dbDelta())
        ? snapshots.call()
        : null;
  }

  private JsonNode post(
      HttpClient http,
      CookieManager cookies,
      OnlineScenario sc,
      ScreenId screen,
      OnlineScenario.Step step)
      throws IOException, InterruptedException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("action", step.action().name());
    body.put("fields", step.fields());
    HttpRequest.Builder req =
        HttpRequest.newBuilder(
                baseUri.resolve("/api/v1/screens/" + screen.name().toLowerCase(Locale.ROOT)))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-XSRF-TOKEN", csrfToken(cookies))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    if (sc.auth() != null) {
      String token = sc.auth().user() + ":" + sc.auth().password();
      req.header(
          "Authorization",
          "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
    }
    HttpResponse<String> r = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    if (r.statusCode() == 404 || r.statusCode() == 401 || r.statusCode() == 403) {
      return null;
    }
    return json.readTree(r.body());
  }

  /**
   * Double-submit CSRF (O1 {@code CookieCsrfTokenRepository}): reuse the token cookie the app set,
   * or plant one; the header must carry the same value.
   */
  private String csrfToken(CookieManager cookies) {
    CookieStore store = cookies.getCookieStore();
    for (HttpCookie c : store.get(baseUri)) {
      if (CSRF_COOKIE.equals(c.getName()) && !c.getValue().isEmpty()) {
        return c.getValue();
      }
    }
    HttpCookie c = new HttpCookie(CSRF_COOKIE, UUID.randomUUID().toString());
    c.setPath("/");
    c.setVersion(0);
    store.add(baseUri, c);
    return c.getValue();
  }

  static List<String> compare(String where, OnlineScenario.Expect e, JsonNode resp) {
    List<String> out = new ArrayList<>();
    if (e.screen() != null && !e.screen().name().equals(text(resp.get("screen")))) {
      out.add(where + ": screen expected " + e.screen() + " got " + text(resp.get("screen")));
    }
    if (e.checkNextScreen()) {
      String exp = e.nextScreen() == null ? null : e.nextScreen().name();
      if (!Objects.equals(exp, text(resp.get("nextScreen")))) {
        out.add(where + ": nextScreen expected " + exp + " got " + text(resp.get("nextScreen")));
      }
    }
    if (e.message() != null) {
      List<String> msgs = new ArrayList<>();
      resp.path("messages").forEach(m -> msgs.add(m.path("text").asText().strip()));
      if (!msgs.contains(e.message().strip())) {
        out.add(where + ": message expected [" + e.message() + "] got " + msgs);
      }
    }
    if (e.fieldErrors() != null) {
      List<String> fields = new ArrayList<>();
      resp.path("fieldErrors").forEach(f -> fields.add(f.path("field").asText()));
      if (!fields.equals(e.fieldErrors())) {
        out.add(where + ": fieldErrors expected " + e.fieldErrors() + " got " + fields);
      }
    }
    return out;
  }

  private static String text(JsonNode n) {
    return n == null || n.isNull() ? null : n.asText();
  }
}
