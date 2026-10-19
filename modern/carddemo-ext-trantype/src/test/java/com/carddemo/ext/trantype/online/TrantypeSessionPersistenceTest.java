package com.carddemo.ext.trantype.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import com.carddemo.platform.time.FixedBusinessClock;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Saves of X2 through a session that, like Spring Session JDBC, hands out a deserialized copy on
 * every read and persists only {@code setAttribute}.
 */
class TrantypeSessionPersistenceTest {

  private InMemoryTransactionTypeStore store;
  private MockMvc mvc;
  private final MockHttpSession session = new SerializingSession();

  @BeforeEach
  void setUp() {
    store = InMemoryTransactionTypeStore.seeded();
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
  void cttuEnterValidatesTheEditedDescriptionAndPf5SavesIt() throws Exception {
    mvc.perform(get("/api/v1/screens/cotrtup").session(session))
        .andExpect(jsonPath("$.data.INFOMSG").value(CttuService.PROMPT_SEARCH));
    send("cotrtup", "ENTER", "{\"TRTYPCD\":\"01\",\"TRTYDSC\":\"\"}")
        .andExpect(jsonPath("$.data.TRTYDSC").value(startsWith("Purchase")));
    send("cotrtup", "ENTER", "{\"TRTYPCD\":\"01\",\"TRTYDSC\":\"Purchases\"}")
        .andExpect(jsonPath("$.data.TRTYDSC").value(startsWith("Purchases")))
        .andExpect(jsonPath("$.data.INFOMSG").value(CttuService.PROMPT_CONFIRM));
    send("cotrtup", "PF5", "{\"TRTYPCD\":\"01\",\"TRTYDSC\":\"Purchases\"}")
        .andExpect(
            jsonPath("$.data.ERRMSG")
                .value(org.hamcrest.Matchers.not(CttuService.MSG_INVALID_KEY)));

    assertThat(store.types.get("01").strip()).isEqualTo("Purchases");
  }

  @Test
  void ctliInlineUpdateWithPf10IsSaved() throws Exception {
    mvc.perform(get("/api/v1/screens/cotrtli").session(session))
        .andExpect(jsonPath("$.data.TRTTYP1").value("01"));
    send("cotrtli", "ENTER", "{\"TRTSEL1\":\"U\"}")
        .andExpect(jsonPath("$.data.INFOMSG").value(CtliService.INFO_UPDATE));
    send("cotrtli", "PF10", "{\"TRTSEL1\":\"U\",\"TRTYPD1\":\"Purchases\"}")
        .andExpect(jsonPath("$.data.INFOMSG").value(CtliService.INFO_UPDATE_DONE));

    assertThat(store.types.get("01").strip()).isEqualTo("Purchases");
    mvc.perform(get("/api/v1/screens/cotrtli").session(session))
        .andExpect(jsonPath("$.data.TRTYPD1").value(startsWith("Purchases")));
  }

  private ResultActions send(String screen, String action, String fields) throws Exception {
    return mvc.perform(
        post("/api/v1/screens/" + screen)
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"" + action + "\",\"fields\":" + fields + "}"));
  }

  private static final class SerializingSession extends MockHttpSession {
    @Override
    public Object getAttribute(String name) {
      Object v = super.getAttribute(name);
      return v == null ? null : copy(v);
    }

    private static Object copy(Object v) {
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
          out.writeObject(v);
        }
        try (ObjectInputStream in =
            new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
          return in.readObject();
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
