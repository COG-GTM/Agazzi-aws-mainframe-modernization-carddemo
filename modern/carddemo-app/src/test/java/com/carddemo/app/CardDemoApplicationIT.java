package com.carddemo.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.port.UserCredentialService.Outcome;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.AppUserRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionCategoryRepository;
import com.carddemo.domain.repository.TransactionTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Boots the whole app on PostgreSQL 16 (Testcontainers): Flyway V1-V3 + seed, JPA validate. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CardDemoApplicationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired TestRestTemplate http;
  @Autowired AccountRepository accounts;
  @Autowired CardRepository cards;
  @Autowired CardXrefRepository xrefs;
  @Autowired CustomerRepository customers;
  @Autowired TransactionTypeRepository types;
  @Autowired TransactionCategoryRepository categories;
  @Autowired DisclosureGroupRepository groups;
  @Autowired TranCatBalanceRepository balances;
  @Autowired DailyTransactionRepository daily;
  @Autowired AppUserRepository users;
  @Autowired UserCredentialService credentials;
  @Autowired BusinessClock clock;

  @Test
  void healthIsUp() {
    var resp = http.getForEntity("/actuator/health", String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void seedHasPlanCounts() {
    assertThat(accounts.count()).isEqualTo(50);
    assertThat(cards.count()).isEqualTo(50);
    assertThat(xrefs.count()).isEqualTo(50);
    assertThat(customers.count()).isEqualTo(50);
    assertThat(types.count()).isEqualTo(7);
    assertThat(categories.count()).isEqualTo(18);
    assertThat(groups.count()).isEqualTo(51);
    assertThat(balances.count()).isEqualTo(50);
    assertThat(daily.count()).isEqualTo(300);
    assertThat(users.count()).isEqualTo(10);
  }

  @Test
  void legacySeedCredentialsAndFixedClock() {
    assertThat(credentials.authenticate("admin001", "PASSWORD").outcome()).isEqualTo(Outcome.OK);
    assertThat(credentials.authenticate("ADMIN001", "WRONG").outcome())
        .isEqualTo(Outcome.WRONG_PASSWORD);
    assertThat(credentials.authenticate("NOBODY", "x").outcome()).isEqualTo(Outcome.USER_NOT_FOUND);
    assertThat(clock.cobolTimestamp()).isEqualTo("2022-07-18-12.00.00.000000");
  }

  @Test
  void protectedEndpointsRequireAuthentication() {
    assertThat(http.getForEntity("/api/v1/screens/cosgn00", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
