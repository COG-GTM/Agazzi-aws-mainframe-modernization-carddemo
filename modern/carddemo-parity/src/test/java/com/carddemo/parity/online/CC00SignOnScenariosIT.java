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
 * O1 online goldens against the real app (PostgreSQL 16 + seed): CC00 sign-on (COSGN00C), CM00 main
 * menu (COMEN01C) and CA00 admin menu (COADM01C).
 */
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
    return scenarios("CC00");
  }

  @TestFactory
  Stream<DynamicTest> cm00() throws Exception {
    return scenarios("CM00");
  }

  @TestFactory
  Stream<DynamicTest> ca00() throws Exception {
    return scenarios("CA00");
  }

  private Stream<DynamicTest> scenarios(String tranid) throws Exception {
    OnlineScenarioRunner runner =
        new OnlineScenarioRunner(URI.create("http://localhost:" + port), this::snapshot);
    List<OnlineScenario> all = OnlineScenario.loadAll(ParityPaths.onlineGoldens().resolve(tranid));
    assertThat(all).isNotEmpty().noneMatch(OnlineScenario::pending);
    return all.stream()
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
