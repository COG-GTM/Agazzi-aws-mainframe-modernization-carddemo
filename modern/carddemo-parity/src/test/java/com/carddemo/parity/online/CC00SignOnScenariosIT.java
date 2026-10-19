package com.carddemo.parity.online;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.app.CardDemoApplication;
import com.carddemo.parity.ParityPaths;
import com.carddemo.parity.db.DbSnapshot;
import java.net.URI;
import java.sql.Connection;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CC00 sign-on scenarios against the real app. Tagged {@code pending-o1}: excluded by {@code
 * failsafe.excludedGroups} until O1 implements COSGN00 (O1 removes the tag in its PR).
 */
@Tag("pending-o1")
@SpringBootTest(
    classes = CardDemoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CC00SignOnScenariosIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @LocalServerPort int port;
  @Autowired DataSource dataSource;

  @TestFactory
  Stream<DynamicTest> cc00() throws Exception {
    OnlineScenarioRunner runner =
        new OnlineScenarioRunner(URI.create("http://localhost:" + port), this::snapshot);
    return OnlineScenario.loadAll(ParityPaths.onlineGoldens().resolve("CC00")).stream()
        .map(
            s ->
                DynamicTest.dynamicTest(
                    s.id() + " " + s.title(),
                    () -> assertThat(runner.run(s).mismatches()).isEmpty()));
  }

  private DbSnapshot snapshot() throws Exception {
    try (Connection c = dataSource.getConnection()) {
      return DbSnapshot.take(c, List.of("app_user"));
    }
  }
}
