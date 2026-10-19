package com.carddemo.online.account.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.online.account.testapp.AccountTestApplication;
import com.carddemo.online.account.testapp.Screens;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/** The O2 resources implement exactly the o2-account operations of openapi.yaml. */
@SpringBootTest(classes = AccountTestApplication.class)
@AutoConfigureMockMvc
class OpenApiContractTest {

  @Autowired MockMvc mvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  @SuppressWarnings("unchecked")
  void everyO2OperationIsMappedAndAnswersScreenResponse() throws Exception {
    Map<String, Object> api;
    try (InputStream in = getClass().getResourceAsStream("/openapi/openapi.yaml")) {
      api = new Yaml().load(in);
    }
    Set<String> expected = new HashSet<>();
    ((Map<String, Map<String, Map<String, Object>>>) api.get("paths"))
        .forEach(
            (path, ops) ->
                ops.forEach(
                    (verb, op) -> {
                      if (String.valueOf(op.get("tags")).contains("o2-account")) {
                        expected.add(verb.toUpperCase() + " " + path);
                      }
                    }));
    assertThat(expected).hasSize(4);
    Set<String> actual = new HashSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (info, handler) -> {
              if (handler
                  .getBeanType()
                  .getPackageName()
                  .startsWith("com.carddemo.online.account")) {
                info.getMethodsCondition()
                    .getMethods()
                    .forEach(
                        m -> info.getPatternValues().forEach(p -> actual.add(m.name() + " " + p)));
              }
            });
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    for (String screen : new String[] {"coactvw", "coactup"}) {
      JsonNode r = new Screens(mvc, "USER0001", screen).first();
      assertThat(r.has("screen") && r.has("data") && r.has("messages")).isTrue();
      assertThat(r.has("fieldErrors") && r.has("context") && r.has("nextScreen")).isTrue();
      assertThat(r.path("data").path("TRNNAME").asText()).isIn("CAVW", "CAUP");
    }
  }
}
