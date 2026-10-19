package com.carddemo.parity.online;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ScreenId;
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
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

/**
 * Online parity scenario (plan §11.4), one YAML file under {@code golden/online/<TRANID>/}. Format:
 *
 * <pre>
 * id: CC00-001
 * title: blank user id
 * tranid: CC00
 * screen: COSGN00
 * status: pending-o1          # pending-&lt;ws&gt; until the owning workstream implements it
 * auth: {user: ADMIN001, password: PASSWORD}   # optional, HTTP Basic
 * steps:
 *   - action: ENTER
 *     fields: {USERID: "", PASSWD: ""}
 *     expect:
 *       screen: COSGN00
 *       nextScreen: null       # or e.g. COADM01
 *       message: "Please enter User ID ..."
 *       fieldErrors: [USERID]
 *       dbDelta: none          # none | any
 * </pre>
 */
public record OnlineScenario(
    String id,
    String title,
    String tranid,
    ScreenId screen,
    String status,
    Auth auth,
    List<Step> steps,
    Path source) {

  /** HTTP Basic credentials of the scenario user (seed users of DUSRSECJ). */
  public record Auth(String user, String password) {}

  /** One user interaction: AID key plus input fields, and the expected response. */
  public record Step(Action action, Map<String, Object> fields, Expect expect) {}

  /** Expected response; {@code null} members are not checked. */
  public record Expect(
      ScreenId screen,
      boolean checkNextScreen,
      ScreenId nextScreen,
      String message,
      List<String> fieldErrors,
      String dbDelta) {}

  public boolean pending() {
    return status != null && status.startsWith("pending");
  }

  public static List<OnlineScenario> loadAll(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> s = Files.walk(dir)) {
      List<OnlineScenario> out = new ArrayList<>();
      for (Path p : s.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
        out.add(load(p));
      }
      return out;
    }
  }

  public static OnlineScenario load(Path file) throws IOException {
    try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return parse(new Yaml().load(r), file);
    }
  }

  @SuppressWarnings("unchecked")
  static OnlineScenario parse(Map<String, Object> y, Path source) {
    Auth auth = null;
    if (y.get("auth") instanceof Map<?, ?> a) {
      auth = new Auth(String.valueOf(a.get("user")), String.valueOf(a.get("password")));
    }
    List<Step> steps = new ArrayList<>();
    for (Map<String, Object> s : (List<Map<String, Object>>) y.getOrDefault("steps", List.of())) {
      Map<String, Object> e = (Map<String, Object>) s.getOrDefault("expect", Map.of());
      steps.add(
          new Step(
              Action.valueOf(String.valueOf(s.getOrDefault("action", "ENTER"))),
              new LinkedHashMap<>((Map<String, Object>) s.getOrDefault("fields", Map.of())),
              new Expect(
                  screen(e.get("screen")),
                  e.containsKey("nextScreen"),
                  screen(e.get("nextScreen")),
                  e.get("message") == null ? null : String.valueOf(e.get("message")),
                  e.get("fieldErrors") == null ? null : (List<String>) e.get("fieldErrors"),
                  e.get("dbDelta") == null ? null : String.valueOf(e.get("dbDelta")))));
    }
    return new OnlineScenario(
        String.valueOf(y.get("id")),
        String.valueOf(y.getOrDefault("title", "")),
        String.valueOf(y.get("tranid")),
        screen(y.get("screen")),
        (String) y.get("status"),
        auth,
        List.copyOf(steps),
        source);
  }

  private static ScreenId screen(Object v) {
    return v == null ? null : ScreenId.valueOf(String.valueOf(v).toUpperCase(Locale.ROOT));
  }
}
