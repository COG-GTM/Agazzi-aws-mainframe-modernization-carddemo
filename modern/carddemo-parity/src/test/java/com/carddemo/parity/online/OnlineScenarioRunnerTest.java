package com.carddemo.parity.online;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.parity.ParityPaths;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Proves the YAML runner against a stub of the contracts-v1 screen endpoint. */
class OnlineScenarioRunnerTest {

  private HttpServer server;
  private final AtomicReference<String> reply = new AtomicReference<>();
  private final AtomicReference<String> lastBody = new AtomicReference<>();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/screens/cosgn00",
        ex -> {
          lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] b = reply.get().getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(200, b.length);
          ex.getResponseBody().write(b);
          ex.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private OnlineScenarioRunner runner() {
    return new OnlineScenarioRunner(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()), null);
  }

  private static OnlineScenario cc00(String file) throws Exception {
    return OnlineScenario.load(ParityPaths.onlineGoldens().resolve("CC00").resolve(file));
  }

  @Test
  void passesWhenResponseMatches() throws Exception {
    reply.set(
        """
        {"screen":"COSGN00","data":null,"messages":[{"severity":"ERROR","text":"Please enter User ID ...   "}],
         "nextScreen":null,"fieldErrors":[{"field":"USERID","message":"required"}],"context":null}
        """);
    var r = runner().run(cc00("001-blank-userid.yaml"));
    assertThat(r.mismatches()).isEmpty();
    assertThat(lastBody.get()).contains("\"action\":\"ENTER\"").contains("\"PASSWD\":\"PASSWORD\"");
  }

  @Test
  void reportsEveryMismatch() throws Exception {
    reply.set(
        """
        {"screen":"COSGN00","messages":[],"nextScreen":"COMEN01","fieldErrors":[]}
        """);
    var r = runner().run(cc00("004-wrong-password.yaml"));
    assertThat(r.passed()).isFalse();
    assertThat(r.mismatches()).hasSize(3);
    assertThat(String.join("\n", r.mismatches()))
        .contains("nextScreen")
        .contains("message")
        .contains("fieldErrors");
  }

  @Test
  void missingEndpointIsAFailureNotAPass() {
    var sc =
        new OnlineScenario(
            "X",
            "",
            "CM00",
            ScreenId.COMEN01,
            null,
            null,
            List.of(
                new OnlineScenario.Step(
                    com.carddemo.contracts.screen.Action.ENTER,
                    java.util.Map.of(),
                    new OnlineScenario.Expect(null, false, null, null, null, null))),
            null);
    assertThat(runner().run(sc).mismatches()).singleElement().asString().contains("not available");
  }

  @Test
  void allCommittedScenariosParse() throws Exception {
    List<OnlineScenario> all = OnlineScenario.loadAll(ParityPaths.onlineGoldens());
    assertThat(all).hasSizeGreaterThanOrEqualTo(8);
    assertThat(all)
        .allSatisfy(
            s -> {
              assertThat(s.steps()).isNotEmpty();
              assertThat(s.screen()).isNotNull();
            });
    assertThat(all).filteredOn(s -> s.tranid().equals("CC00")).noneMatch(OnlineScenario::pending);
  }
}
