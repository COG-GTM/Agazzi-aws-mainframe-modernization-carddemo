package com.carddemo.online.transaction.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O4 parity gate: every golden under {@code golden/o4-transaction/online/} against the real
 * controllers, services and PostgreSQL 16 (Flyway V1 + V2 seed + V1300), DB reset per scenario.
 */
@Tag("parity")
@SpringBootTest(
    classes = O4TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("o4test")
@Testcontainers
class O4OnlineParityTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @LocalServerPort int port;
  @Autowired Flyway flyway;
  @Autowired JdbcTemplate jdbc;

  static Path goldens() {
    return Path.of(System.getProperty("modern.root", ".."))
        .toAbsolutePath()
        .normalize()
        .resolve("carddemo-parity/src/test/resources/golden/o4-transaction/online");
  }

  @TestFactory
  Stream<DynamicTest> o4Goldens() throws Exception {
    List<Path> files;
    try (Stream<Path> s = Files.walk(goldens())) {
      files = s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
    }
    assertThat(files).hasSizeGreaterThan(40);
    O4ScenarioRunner runner = new O4ScenarioRunner(URI.create("http://localhost:" + port), jdbc);
    return files.stream()
        .map(
            f ->
                DynamicTest.dynamicTest(
                    goldens().relativize(f).toString(),
                    () -> {
                      Map<String, Object> sc = O4ScenarioRunner.load(f);
                      flyway.clean();
                      flyway.migrate();
                      assertThat(runner.run(sc)).isEmpty();
                    }));
  }
}
