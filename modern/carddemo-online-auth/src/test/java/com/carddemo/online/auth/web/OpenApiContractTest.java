package com.carddemo.online.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.carddemo.domain.repository.AppUserRepository;
import com.carddemo.online.auth.AuthTestApplication;
import com.carddemo.online.auth.SeedUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.yaml.snakeyaml.Yaml;

/**
 * Contract test against {@code openapi/openapi.yaml} of carddemo-contracts (contracts-v1): every O1
 * operation exists, answers 200 with the documented ScreenResponse shape, the data object only uses
 * fields of the map schema and {@code screen} is in the schema enum.
 */
@SpringBootTest(classes = AuthTestApplication.class)
@AutoConfigureMockMvc
class OpenApiContractTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean AppUserRepository users;

  @BeforeEach
  void seed() {
    SeedUsers.stub(users);
  }

  static Map<String, Object> openapi() throws Exception {
    try (InputStream in =
        OpenApiContractTest.class.getClassLoader().getResourceAsStream("openapi/openapi.yaml")) {
      assertThat(in).as("openapi.yaml of carddemo-contracts on the classpath").isNotNull();
      return new Yaml().load(in);
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Object o) {
    return (Map<String, Object>) o;
  }

  static Stream<Arguments> operations() {
    return Stream.of(
        Arguments.of("cosgn00", "get", "Cosgn00", null, null),
        Arguments.of("cosgn00", "post", "Cosgn00", "{\"USERID\":\"\",\"PASSWD\":\"\"}", null),
        Arguments.of("comen01", "get", "Comen01", null, "USER0001"),
        Arguments.of("comen01", "post", "Comen01", "{\"OPTION\":\"99\"}", "USER0001"),
        Arguments.of("coadm01", "get", "Coadm01", null, "ADMIN001"),
        Arguments.of("coadm01", "post", "Coadm01", "{\"OPTION\":\"\"}", "ADMIN001"));
  }

  @ParameterizedTest(name = "{1} /api/v1/screens/{0}")
  @MethodSource("operations")
  void responseMatchesSchema(String map, String method, String schema, String fields, String user)
      throws Exception {
    Map<String, Object> api = openapi();
    Map<String, Object> path = map(map(api.get("paths")).get("/api/v1/screens/" + map));
    assertThat(path).as("path in openapi").isNotNull().containsKey(method);
    assertThat(map(map(path.get(method)).get("responses"))).containsKey("200");
    Map<String, Object> schemas = map(map(api.get("components")).get("schemas"));
    Map<String, Object> response = map(schemas.get(schema + "Response"));
    Map<String, Object> output = map(map(schemas.get(schema + "Output")).get("properties"));
    if (fields != null) {
      Map<String, Object> input = map(map(schemas.get(schema + "Input")).get("properties"));
      assertThat(input.keySet())
          .containsAll(json.readTree(fields).properties().stream().map(Map.Entry::getKey).toList());
    }

    var req =
        "get".equals(method)
            ? get("/api/v1/screens/" + map)
            : post("/api/v1/screens/" + map)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ENTER\",\"fields\":" + fields + "}");
    if (user != null) {
      req.with(httpBasic(user, "PASSWORD"));
    }
    RequestBuilder rb = req;
    var res = mvc.perform(rb).andReturn().getResponse();
    assertThat(res.getStatus()).isEqualTo(200);
    JsonNode body = json.readTree(res.getContentAsString());

    Map<String, Object> props = map(response.get("properties"));
    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) response.get("required");
    for (String r : required) {
      assertThat(body.has(r)).as("required " + r).isTrue();
    }
    assertThat(names(body)).isSubsetOf(props.keySet());
    @SuppressWarnings("unchecked")
    List<String> screens = (List<String>) map(props.get("screen")).get("enum");
    assertThat(screens).contains(body.get("screen").asText());
    assertThat(names(body.get("data"))).isSubsetOf(output.keySet());
    for (String f : names(body.get("data"))) {
      JsonNode v = body.get("data").get(f);
      Object max = map(output.get(f)).get("maxLength");
      if (v.isTextual() && max != null) {
        assertThat(v.asText().length()).as(f + " maxLength").isLessThanOrEqualTo((Integer) max);
      }
    }
    for (JsonNode m : body.get("messages")) {
      assertThat(Set.of("INFO", "ERROR")).contains(m.get("severity").asText());
    }
  }

  static Set<String> names(JsonNode n) {
    Set<String> out = new TreeSet<>();
    if (n != null && n.isObject()) {
      List<String> l = new ArrayList<>();
      n.fieldNames().forEachRemaining(l::add);
      out.addAll(l);
    }
    return out;
  }
}
