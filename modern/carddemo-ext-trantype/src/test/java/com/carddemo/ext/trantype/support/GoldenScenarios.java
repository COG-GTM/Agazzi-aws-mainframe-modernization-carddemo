package com.carddemo.ext.trantype.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.yaml.snakeyaml.Yaml;

/**
 * Runs the X2 online goldens ({@code golden/x2-trantype/online/<TRANID>/*.yaml}, same format as
 * {@code carddemo-parity} {@code OnlineScenario}) against MockMvc. Like {@code
 * OnlineScenarioRunner} every step is a POST without cookies and follows {@code nextScreen}; {@code
 * dbDelta} is {@code none}, {@code any} or a list of {@code KIND table key}.
 */
public final class GoldenScenarios {

  public static final Path ROOT =
      Path.of("..", "carddemo-parity", "src", "test", "resources", "golden", "x2-trantype");

  private static final ObjectMapper JSON = new ObjectMapper();

  private GoldenScenarios() {}

  public static List<Path> online(String tranId) {
    try (Stream<Path> s = Files.list(ROOT.resolve("online").resolve(tranId))) {
      return s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> load(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      return (Map<String, Object>) new Yaml().load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param request customises each request (principal, Basic auth)
   * @param snapshot current {@code transaction_type} rows (key to description)
   * @return mismatches, empty when the scenario is green
   */
  @SuppressWarnings("unchecked")
  public static List<String> run(
      MockMvc mvc,
      Path file,
      java.util.function.UnaryOperator<MockHttpServletRequestBuilder> request,
      Supplier<Map<String, String>> snapshot)
      throws Exception {
    Map<String, Object> sc = load(file);
    List<String> out = new ArrayList<>();
    String screen = (String) sc.get("screen");
    List<Map<String, Object>> steps = (List<Map<String, Object>>) sc.get("steps");
    for (int i = 0; i < steps.size(); i++) {
      Map<String, Object> step = steps.get(i);
      Map<String, Object> exp = (Map<String, Object>) step.get("expect");
      String where =
          "%s step %d (%s on %s)".formatted(sc.get("id"), i + 1, step.get("action"), screen);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("action", step.get("action"));
      body.put("fields", step.get("fields") == null ? Map.of() : step.get("fields"));
      Map<String, String> before = new LinkedHashMap<>(snapshot.get());
      String resp =
          mvc.perform(
                  request.apply(
                      post("/api/v1/screens/" + screen.toLowerCase(Locale.ROOT))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(JSON.writeValueAsString(body))))
              .andReturn()
              .getResponse()
              .getContentAsString();
      if (resp.isEmpty()) {
        out.add(where + ": endpoint not available");
        break;
      }
      JsonNode r = JSON.readTree(resp);
      compare(where, exp, r, out);
      List<String> delta = InMemoryTransactionTypeStore.delta(before, snapshot.get());
      Object dbDelta = exp.get("dbDelta");
      if ("none".equals(dbDelta) && !delta.isEmpty()) {
        out.add(where + ": expected no DB delta, got " + delta);
      } else if (dbDelta instanceof List<?> l && !l.equals(delta)) {
        out.add(where + ": dbDelta expected " + l + " got " + delta);
      }
      JsonNode next = r.get("nextScreen");
      if (next != null && !next.isNull()) {
        screen = next.asText();
      }
    }
    return out;
  }

  private static void compare(String where, Map<String, Object> e, JsonNode r, List<String> out) {
    if (e.get("screen") != null && !e.get("screen").equals(r.path("screen").asText())) {
      out.add(where + ": screen expected " + e.get("screen") + " got " + r.path("screen"));
    }
    if (e.containsKey("nextScreen")) {
      String got =
          r.get("nextScreen") == null || r.get("nextScreen").isNull()
              ? null
              : r.get("nextScreen").asText();
      if (!Objects.equals(e.get("nextScreen"), got)) {
        out.add(where + ": nextScreen expected " + e.get("nextScreen") + " got " + got);
      }
    }
    if (e.get("message") != null) {
      List<String> msgs = new ArrayList<>();
      r.path("messages").forEach(m -> msgs.add(m.path("text").asText().strip()));
      if (!msgs.contains(e.get("message").toString().strip())) {
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
  }
}
