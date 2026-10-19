package com.carddemo.ext.authorization.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.ext.authorization.MutableBusinessClock;
import com.carddemo.ext.authorization.X1Fixtures;
import com.carddemo.ext.authorization.batch.PendingAuthUnloadLoadService;
import com.carddemo.ext.authorization.codec.AuthorizationReply;
import com.carddemo.ext.authorization.codec.AuthorizationRequest;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.service.AuthFraudService;
import com.carddemo.ext.authorization.service.AuthorizationDecisionService;
import com.carddemo.ext.authorization.store.AuthFraudRepository;
import com.carddemo.ext.authorization.store.AuthKey;
import com.carddemo.ext.authorization.store.PendingAuthDetailRepository;
import com.carddemo.ext.authorization.store.PendingAuthSummaryRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** X1 on PostgreSQL 16: Flyway V1-V3 + V2000/V2001, JPA validate, CP00/PF5/CBPAUP0J/DBPAUTP0. */
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.batch.jdbc.initialize-schema=never",
      "spring.datasource.url="
    })
@Testcontainers
class X1PostgresIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static final String CARD = "4999999999999999";

  @Autowired JdbcTemplate jdbc;
  @Autowired AccountRepository accounts;
  @Autowired CustomerRepository customers;
  @Autowired CardXrefRepository xrefs;
  @Autowired PendingAuthSummaryRepository summaries;
  @Autowired PendingAuthDetailRepository details;
  @Autowired AuthFraudRepository frauds;
  @Autowired AuthorizationDecisionService decisions;
  @Autowired AuthFraudService fraud;
  @Autowired PendingAuthUnloadLoadService unloadLoad;
  @Autowired MutableBusinessClock clock;
  @Autowired JobLauncher launcher;

  @Autowired
  @Qualifier("cbpaup0jJob")
  Job cbpaup0j;

  @Test
  void flywayAppliesX1MigrationsAndAuthflowPersists() throws Exception {
    List<String> versions =
        jdbc.queryForList(
            "select version from flyway_schema_history where success order by installed_rank",
            String.class);
    assertThat(versions).contains("1", "2000", "2001");

    frauds.deleteAll();
    details.deleteAll();
    summaries.deleteAll();
    accounts.save(X1Fixtures.account(90000000001L, "1000.00", "0.00"));
    customers.save(X1Fixtures.customer(900000001));
    xrefs.save(X1Fixtures.xref(CARD, 900000001, 90000000001L));
    clock.set(LocalDateTime.of(2022, 7, 18, 12, 0));
    AuthorizationReply ok =
        decisions.process(AuthorizationRequest.parse(X1Fixtures.request(CARD, "100.00", "IT1")));
    assertThat(ok.authRespCode()).isEqualTo("00");
    AuthorizationReply ko =
        decisions.process(AuthorizationRequest.parse(X1Fixtures.request(CARD, "5000.00", "IT2")));
    assertThat(ko.authRespReason()).isEqualTo("4100");

    PendingAuthDetail d =
        details.findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc(90000000001L).get(0);
    AuthKey key = new AuthKey(d.getAuthDate9c(), d.getAuthTime9c());
    assertThat(fraud.toggle(90000000001L, 900000001L, key)).contains("AUTH MARKED FRAUD...");
    assertThat(fraud.toggle(90000000001L, 900000001L, key)).contains("AUTH FRAUD REMOVED...");
    assertThat(
            jdbc.queryForObject(
                "select auth_fraud from auth_fraud where card_num = ?", String.class, CARD))
        .isEqualTo("R");

    clock.set(LocalDateTime.of(2022, 7, 30, 12, 0));
    var exec =
        launcher.run(
            cbpaup0j,
            new JobParametersBuilder()
                .addString("sysin", "05,00001,00001,N")
                .addLong("run", System.nanoTime())
                .toJobParameters());
    assertThat(exec.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(details.findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc(90000000001L)).isEmpty();
  }

  @Test
  void dbpautp0RawUnloadLoadsIntoPostgres() throws Exception {
    frauds.deleteAll();
    details.deleteAll();
    summaries.deleteAll();
    byte[] raw =
        Files.readAllBytes(
            Path.of(
                System.getProperty("modern.root", ".."),
                "../app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat"));
    var r = unloadLoad.loadHdUnload(raw);
    assertThat(r.childrenLoaded()).isEqualTo(202);
    assertThat(summaries.count()).isEqualTo(r.rootsLoaded());
    assertThat(details.count()).isEqualTo(202);
  }
}
