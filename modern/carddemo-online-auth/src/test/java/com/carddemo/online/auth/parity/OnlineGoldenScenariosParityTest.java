package com.carddemo.online.auth.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.carddemo.domain.repository.AppUserRepository;
import com.carddemo.online.auth.AuthTestApplication;
import com.carddemo.online.auth.SeedUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/**
 * Parity gate of O1 without Docker: runs the online goldens of CC00/CM00/CA00 ({@code
 * carddemo-parity/src/test/resources/golden/online}) with the same comparison rules as {@code
 * OnlineScenarioRunner} (screen, nextScreen, message after strip, fieldErrors, no DB writes). The
 * same files run against the full app in {@code CC00SignOnScenariosIT}.
 */
@Tag("parity")
@SpringBootTest(classes = AuthTestApplication.class)
@AutoConfigureMockMvc
class OnlineGoldenScenariosParityTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean AppUserRepository users;

  static Path goldens() {
    return Path.of(System.getProperty("modern.root", ".."))
        .toAbsolutePath()
        .normalize()
        .resolve("carddemo-parity/src/test/resources/golden/online");
  }

  @TestFactory
  Stream<DynamicTest> o1Goldens() throws Exception {
    List<Path> files = new ArrayList<>();
    for (String tran : List.of("CC00", "CM00", "CA00")) {
      try (Stream<Path> s = Files.list(goldens().resolve(tran))) {
        files.addAll(s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList());
      }
    }
    assertThat(files).hasSizeGreaterThanOrEqualTo(38);
    return files.stream()
        .map(f -> DynamicTest.dynamicTest(f.getFileName().toString(), () -> run(load(f))));
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> load(Path f) throws Exception {
    try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
      return new Yaml().load(r);
    }
  }

  @SuppressWarnings("unchecked")
  private void run(Map<String, Object> sc) throws Exception {
    assertThat(sc.get("status")).as("no pending status").isNull();
    SeedUsers.stub(users);
    clearInvocations(users);
    String current = String.valueOf(sc.get("screen"));
    Map<String, Object> auth = (Map<String, Object>) sc.get("auth");
    List<String> mismatches = new ArrayList<>();
    for (Map<String, Object> step : (List<Map<String, Object>>) sc.get("steps")) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("action", step.get("action"));
      body.put("fields", step.get("fields"));
      var req =
          post("/api/v1/screens/" + current.toLowerCase(Locale.ROOT))
              .contentType(MediaType.APPLICATION_JSON)
              .content(json.writeValueAsString(body));
      if (auth != null) {
        req.with(httpBasic(String.valueOf(auth.get("user")), String.valueOf(auth.get("password"))));
      }
      var res = mvc.perform(req).andReturn().getResponse();
      String where = sc.get("id") + " " + step.get("action") + " on " + current;
      if (res.getStatus() != 200) {
        mismatches.add(where + ": HTTP " + res.getStatus());
        break;
      }
      JsonNode resp = json.readTree(res.getContentAsString());
      Map<String, Object> e = (Map<String, Object>) step.get("expect");
      if (e.get("screen") != null && !e.get("screen").equals(text(resp, "screen"))) {
        mismatches.add(where + ": screen " + text(resp, "screen"));
      }
      if (e.containsKey("nextScreen")
          && !Objects.equals(e.get("nextScreen"), text(resp, "nextScreen"))) {
        mismatches.add(where + ": nextScreen " + text(resp, "nextScreen"));
      }
      if (e.get("message") != null) {
        List<String> msgs = new ArrayList<>();
        resp.path("messages").forEach(m -> msgs.add(m.path("text").asText().strip()));
        if (!msgs.contains(String.valueOf(e.get("message")).strip())) {
          mismatches.add(where + ": messages " + msgs);
        }
      }
      if (e.get("fieldErrors") != null) {
        List<String> fe = new ArrayList<>();
        resp.path("fieldErrors").forEach(x -> fe.add(x.path("field").asText()));
        if (!fe.equals(e.get("fieldErrors"))) {
          mismatches.add(where + ": fieldErrors " + fe);
        }
      }
      if ("none".equals(e.get("dbDelta"))) {
        verify(users, never()).save(any());
        verify(users, never()).delete(any());
      }
      if (text(resp, "nextScreen") != null) {
        current = text(resp, "nextScreen");
      }
    }
    assertThat(mismatches).isEmpty();
  }

  static String text(JsonNode n, String f) {
    JsonNode v = n.get(f);
    return v == null || v.isNull() ? null : v.asText();
  }
}
