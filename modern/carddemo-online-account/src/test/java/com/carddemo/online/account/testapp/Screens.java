package com.carddemo.online.account.testapp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** One 3270 terminal: an HTTP session of a signed-on user driving a screen resource. */
public final class Screens {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final MockMvc mvc;
  private final String user;
  private final String screen;
  private final MockHttpSession session = new MockHttpSession();

  public Screens(MockMvc mvc, String user, String screen) {
    this.mvc = mvc;
    this.user = user;
    this.screen = screen;
  }

  public JsonNode first() throws Exception {
    return read(
        mvc.perform(
                get("/api/v1/screens/" + screen).session(session).with(httpBasic(user, "PASSWORD")))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  public JsonNode send(String action, String... fieldValuePairs) throws Exception {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (int i = 0; i < fieldValuePairs.length; i += 2) {
      fields.put(fieldValuePairs[i], fieldValuePairs[i + 1]);
    }
    Map<String, Object> body = Map.of("action", action, "fields", fields);
    return read(
        mvc.perform(
                post("/api/v1/screens/" + screen)
                    .session(session)
                    .with(httpBasic(user, "PASSWORD"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(body)))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  public static List<String> messages(JsonNode r) {
    List<String> out = new ArrayList<>();
    r.path("messages").forEach(m -> out.add(m.path("text").asText()));
    return out;
  }

  private static JsonNode read(String s) throws Exception {
    return JSON.readTree(s);
  }
}
