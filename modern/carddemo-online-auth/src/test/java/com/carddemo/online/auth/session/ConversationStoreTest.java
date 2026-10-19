package com.carddemo.online.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ConversationStoreTest {

  private final ConversationStore store = new ConversationStore(new ObjectMapper());

  @Test
  void roundTripsThroughTheSessionAsJson() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    ConversationContext ctx =
        ConversationContext.signedOn("USER0001", UserType.USER)
            .transferTo("CM00", "COMEN01C", "CAVW", "COACTVWC");
    store.save(req, ctx);
    assertThat(req.getSession(false)).as("no session for stateless clients").isNull();
    req.getSession(true);
    store.save(req, ctx);
    assertThat(req.getSession().getAttribute(ConversationStore.ATTRIBUTE))
        .isInstanceOf(String.class);
    assertThat(store.load(req)).contains(ctx);
    store.save(req, null);
    assertThat(store.load(req)).isEmpty();
  }
}
