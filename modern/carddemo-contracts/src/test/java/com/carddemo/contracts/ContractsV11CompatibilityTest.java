package com.carddemo.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.map.CocrdliScreen;
import com.carddemo.contracts.screen.map.CocrdupScreen;
import com.carddemo.contracts.screen.map.CotrtliScreen;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** contracts-v1.1 must stay source and wire compatible with contracts-v1. */
class ContractsV11CompatibilityTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void v1InputConstructorsStillCompileAndLeaveNewFieldsNull() {
    var crdli = new CocrdliScreen.Input("00000000001", "4111111111111111");
    assertThat(crdli.acctsid()).isEqualTo("00000000001");
    assertThat(crdli.crdsel1()).isNull();
    var crdup = new CocrdupScreen.Input("4111111111111111", "NAME", "Y", "12", "2030");
    assertThat(crdup.cardsid()).isEqualTo("4111111111111111");
    assertThat(crdup.acctsid()).isNull();
    assertThat(crdup.expyear()).isEqualTo("2030");
    var trtli = new CotrtliScreen.Input("01", "D", "a", "b", "c", "d", "e", "f", "g");
    assertThat(trtli.trtypd7()).isEqualTo("g");
    assertThat(trtli.trtsel1()).isNull();
  }

  @Test
  void runtimeUnprotectedFieldsAreReadFromJson() throws Exception {
    var in =
        json.readValue(
            "{\"ACCTSID\":\"00000000001\",\"CRDSEL3\":\"S\"}", CocrdliScreen.Input.class);
    assertThat(in.acctsid()).isEqualTo("00000000001");
    assertThat(in.crdsel3()).isEqualTo("S");
    var v1 = json.readValue("{\"CARDSID\":\"4111111111111111\"}", CocrdupScreen.Input.class);
    assertThat(v1.cardsid()).isEqualTo("4111111111111111");
    assertThat(v1.acctsid()).isNull();
    var trt = json.readValue("{\"TRTSEL2\":\"U\",\"TRTYPD2\":\"x\"}", CotrtliScreen.Input.class);
    assertThat(trt.trtsel2()).isEqualTo("U");
    assertThat(trt.trtypd2()).isEqualTo("x");
  }

  @Test
  void conversationContextIsSerializableAndJsonHasNoAdminProperty() throws Exception {
    ConversationContext ctx = ConversationContext.signedOn("ADMIN001", UserType.ADMIN);
    var bytes = new ByteArrayOutputStream();
    try (var out = new ObjectOutputStream(bytes)) {
      out.writeObject(ctx);
    }
    try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      assertThat(in.readObject()).isEqualTo(ctx);
    }
    String text = json.writeValueAsString(ctx);
    assertThat(json.readTree(text).has("admin")).isFalse();
    assertThat(json.readValue(text, ConversationContext.class)).isEqualTo(ctx);
    assertThat(ConversationContext.SESSION_ATTRIBUTE)
        .isEqualTo(ConversationContext.class.getName());
  }

  @Test
  void newPortMethodsHaveCompatibleDefaults() {
    GenerationStore gdg =
        new GenerationStore() {
          public Generation allocate(String base) {
            return new Generation(base, 1);
          }

          public OutputStream openOutput(Generation g) {
            return OutputStream.nullOutputStream();
          }

          public void commit(Generation g) {}

          public void discard(Generation g) {}

          public Optional<Generation> resolve(String base, int relative) {
            return Optional.empty();
          }

          public InputStream openInput(Generation g) {
            return InputStream.nullInputStream();
          }

          public List<Generation> list(String base) {
            return List.of();
          }
        };
    assertThat(gdg.define("REPTFILE", 10)).isTrue();
    assertThat(gdg.limit("REPTFILE")).isEqualTo(5);

    var msg = new ChannelMessage("id", null, "payload", Map.of());
    MessageChannelPort mq =
        new MessageChannelPort() {
          public String send(String channel, String payload, Map<String, String> headers) {
            return "id";
          }

          public Optional<ChannelMessage> receive(String channel, Duration wait) {
            return Optional.of(msg);
          }
        };
    assertThat(mq.receiveUnacknowledged("q", Duration.ZERO)).contains(msg);
    mq.acknowledge("q", msg);
  }
}
