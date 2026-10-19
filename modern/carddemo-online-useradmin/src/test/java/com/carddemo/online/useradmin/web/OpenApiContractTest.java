package com.carddemo.online.useradmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.carddemo.online.useradmin.support.UseradminTestApp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/** Responses of CU00-CU03 match the frozen {@code openapi.yaml} schemas (contracts-v1). */
@SpringBootTest(classes = UseradminTestApp.class)
@AutoConfigureMockMvc
@DirtiesContext
class OpenApiContractTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @SuppressWarnings("unchecked")
  private static Map<String, Object> openapi() throws Exception {
    try (InputStream in =
        OpenApiContractTest.class.getClassLoader().getResourceAsStream("openapi/openapi.yaml")) {
      return new Yaml().load(in);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> schema(Map<String, Object> api, String name) {
    Map<String, Object> comps = (Map<String, Object>) api.get("components");
    return (Map<String, Object>) ((Map<String, Object>) comps.get("schemas")).get(name);
  }

  @SuppressWarnings("unchecked")
  @ParameterizedTest
  @ValueSource(strings = {"Cousr00", "Cousr01", "Cousr02", "Cousr03"})
  void getAndPostConformToSchema(String name) throws Exception {
    Map<String, Object> api = openapi();
    String path = "/api/v1/screens/" + name.toLowerCase();
    Map<String, Object> ops =
        (Map<String, Object>) ((Map<String, Object>) api.get("paths")).get(path);
    assertThat(ops).containsKeys("get", "post");

    Map<String, Object> response = schema(api, name + "Response");
    Map<String, Object> output = schema(api, name + "Output");
    Set<String> outputProps = ((Map<String, Object>) output.get("properties")).keySet();
    Set<String> inputProps =
        ((Map<String, Object>) schema(api, name + "Input").get("properties")).keySet();

    String body =
        mvc.perform(get(path).with(httpBasic("ADMIN001", "PASSWORD")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    check(json.readTree(body), response, outputProps, name);

    StringBuilder fields = new StringBuilder("{");
    for (String p : inputProps) {
      fields.append(fields.length() > 1 ? "," : "").append('"').append(p).append("\":\"\"");
    }
    fields.append('}');
    body =
        mvc.perform(
                post(path)
                    .with(httpBasic("ADMIN001", "PASSWORD"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"action\":\"ENTER\",\"fields\":" + fields + "}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    check(json.readTree(body), response, outputProps, name);
  }

  @SuppressWarnings("unchecked")
  private static void check(
      JsonNode node, Map<String, Object> response, Set<String> outputProps, String name) {
    for (String req : (List<String>) response.get("required")) {
      assertThat(node.has(req)).as(req).isTrue();
    }
    assertThat(node.get("screen").asText()).isEqualTo(name.toUpperCase());
    Set<String> actual = new HashSet<>();
    node.get("data").fieldNames().forEachRemaining(actual::add);
    assertThat(actual).isEqualTo(outputProps);
    Map<String, Object> props = (Map<String, Object>) response.get("properties");
    assertThat(props.keySet()).containsAll(iter(node));
    for (JsonNode m : node.get("messages")) {
      assertThat(m.get("severity").asText()).isIn("INFO", "ERROR");
    }
  }

  private static Set<String> iter(JsonNode node) {
    Set<String> s = new HashSet<>();
    node.fieldNames().forEachRemaining(s::add);
    return s;
  }
}
