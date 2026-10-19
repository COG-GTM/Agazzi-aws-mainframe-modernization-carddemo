package com.carddemo.ext.trantype.online;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import com.carddemo.platform.time.FixedBusinessClock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TrantypeScreenControllerTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    InMemoryTransactionTypeStore store = InMemoryTransactionTypeStore.seeded();
    FixedBusinessClock clock = FixedBusinessClock.golden();
    mvc =
        MockMvcBuilders.standaloneSetup(
                new TrantypeScreenController(
                    new CtliService(store, clock),
                    new CttuService(store, clock),
                    new TrantypeConversations()))
            .build();
  }

  @Test
  void browserSessionFlowGetThenPost() throws Exception {
    MockHttpSession session = new MockHttpSession();
    mvc.perform(get("/api/v1/screens/cotrtli").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.screen").value("COTRTLI"))
        .andExpect(jsonPath("$.data.TRTTYP1").value("01"));
    mvc.perform(
            post("/api/v1/screens/cotrtli")
                .session(session)
                .cookie(new Cookie("JSESSIONID", session.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PF2\",\"fields\":{}}"))
        .andExpect(jsonPath("$.nextScreen").value("COTRTUP"));
    mvc.perform(get("/api/v1/screens/cotrtup").session(session))
        .andExpect(jsonPath("$.data.INFOMSG").value(CttuService.PROMPT_SEARCH));
    mvc.perform(
            post("/api/v1/screens/cotrtup")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PF3\",\"fields\":{}}"))
        .andExpect(jsonPath("$.nextScreen").value("COTRTLI"));
  }

  @Test
  void directCttuEntryFromAdminMenu() throws Exception {
    MockHttpSession session = new MockHttpSession();
    mvc.perform(get("/api/v1/screens/cotrtup").session(session).principal(() -> "ADMIN001"))
        .andExpect(jsonPath("$.context.userId").value("ADMIN001"));
    mvc.perform(get("/api/v1/screens/cotrtup").session(session))
        .andExpect(jsonPath("$.screen").value("COTRTUP"));
  }
}
