package com.carddemo.online.card.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shared PostgreSQL 16 container (one per JVM) for the parity and IT suites. */
@SpringBootTest(classes = O3TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("o3")
public abstract class PostgresTestBase {

  @ServiceConnection
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }
}
