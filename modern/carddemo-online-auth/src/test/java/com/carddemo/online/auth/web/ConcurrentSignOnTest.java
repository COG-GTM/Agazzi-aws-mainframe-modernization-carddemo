package com.carddemo.online.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.carddemo.domain.repository.AppUserRepository;
import com.carddemo.online.auth.AuthTestApplication;
import com.carddemo.online.auth.SeedUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Concurrency: O1 programs never REWRITE (USRSEC is read-only here), so the risk is state leaking
 * between simultaneous conversations. 10 users sign on and navigate in parallel; each one must see
 * only its own COMMAREA and its own menu.
 */
@SpringBootTest(classes = AuthTestApplication.class)
@AutoConfigureMockMvc
class ConcurrentSignOnTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean AppUserRepository users;

  @Test
  void parallelConversationsAreIsolated() throws Exception {
    SeedUsers.stub(users);
    List<String> ids = new ArrayList<>(SeedUsers.seed().keySet());
    ExecutorService pool = Executors.newFixedThreadPool(ids.size());
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int round = 0; round < 5; round++) {
        for (String id : ids) {
          results.add(pool.submit(conversation(id)));
        }
      }
      for (Future<String> f : results) {
        assertThat(f.get()).isEqualTo("ok");
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private Callable<String> conversation(String id) {
    return () -> {
      var r =
          mvc.perform(
                  post("/api/v1/screens/cosgn00")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"action\":\"ENTER\",\"fields\":{\"USERID\":\""
                              + id
                              + "\",\"PASSWD\":\"PASSWORD\"}}"))
              .andReturn();
      JsonNode signOn = json.readTree(r.getResponse().getContentAsString());
      boolean admin = id.startsWith("ADMIN");
      String menu = admin ? "coadm01" : "comen01";
      assertThat(signOn.get("nextScreen").asText()).isEqualTo(menu.toUpperCase());
      MockHttpSession s = (MockHttpSession) r.getRequest().getSession(false);
      JsonNode m =
          json.readTree(
              mvc.perform(get("/api/v1/screens/" + menu).session(s))
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      assertThat(m.get("context").get("userId").asText()).isEqualTo(id);
      assertThat(m.get("data").get("TRNNAME").asText()).isEqualTo(admin ? "CA00" : "CM00");
      return "ok";
    };
  }
}
