package com.carddemo.ext.trantype.it;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/** One PostgreSQL 16 container per JVM, Flyway V1 + seed V2 + V3 + X2 migrations. */
abstract class PostgresSupport {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }
}
