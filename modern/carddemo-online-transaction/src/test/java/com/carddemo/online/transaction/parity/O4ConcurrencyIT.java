package com.carddemo.online.transaction.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.ReportJobLauncherPort;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportRequest;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportType;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.online.transaction.service.Cobil00cService;
import com.carddemo.online.transaction.service.CommareaExtension;
import com.carddemo.online.transaction.service.Cotrn02cService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Concurrency of the REWRITE/WRITE programs (CT02, CB00) on PostgreSQL 16, plus jobs status. */
@SpringBootTest(
    classes = O4TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("o4test")
@Testcontainers
class O4ConcurrencyIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired Cotrn02cService ct02;
  @Autowired Cobil00cService cb00;
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @Autowired ReportJobLauncherPort launcher;
  @Autowired TestRestTemplate rest;

  private static final ConversationContext CTX =
      ConversationContext.signedOn("USER0001", UserType.USER).reenter();

  @BeforeEach
  void reset() {
    flyway.clean();
    flyway.migrate();
  }

  private static <T> List<T> parallel(int n, Callable<T> task) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(n);
    try {
      List<Future<T>> fs = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        fs.add(pool.submit(task));
      }
      List<T> out = new ArrayList<>();
      for (Future<T> f : fs) {
        out.add(f.get());
      }
      return out;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentAddsGetConsecutiveIds() throws Exception {
    Map<String, String> in = new HashMap<>();
    in.put("ACTIDIN", "00000000050");
    in.put("TTYPCD", "01");
    in.put("TCATCD", "0001");
    in.put("TRNSRC", "POS TERM  ");
    in.put("TDESC", "COFFEE");
    in.put("TRNAMT", "+00000001.00");
    in.put("TORIGDT", "2022-07-18");
    in.put("TPROCDT", "2022-07-18");
    in.put("MID", "000000001");
    in.put("MNAME", "BAR");
    in.put("MCITY", "MILANO");
    in.put("MZIP", "20100");
    in.put("CONFIRM", "Y");
    List<String> msgs =
        parallel(
            8,
            () ->
                ct02.run(CTX, CommareaExtension.initial(), Action.ENTER, new HashMap<>(in))
                    .response()
                    .messages()
                    .get(0)
                    .text());
    assertThat(msgs).allMatch(m -> m.startsWith("Transaction added successfully."));
    assertThat(jdbc.queryForList("SELECT tran_id FROM transaction ORDER BY tran_id", String.class))
        .containsExactly(
            "0000000000000001",
            "0000000000000002",
            "0000000000000003",
            "0000000000000004",
            "0000000000000005",
            "0000000000000006",
            "0000000000000007",
            "0000000000000008");
  }

  @Test
  void concurrentPaymentsOnTheSameAccountPayOnce() throws Exception {
    List<String> msgs =
        parallel(
            6,
            () ->
                cb00.run(
                        CTX,
                        CommareaExtension.initial(),
                        Action.ENTER,
                        Map.of("ACTIDIN", "00000000001", "CONFIRM", "Y"))
                    .response()
                    .messages()
                    .get(0)
                    .text());
    assertThat(msgs.stream().filter(m -> m.startsWith("Payment successful.")).count()).isOne();
    assertThat(msgs.stream().filter(m -> m.equals("You have nothing to pay...")).count())
        .isEqualTo(5);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction", Integer.class)).isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT CAST(acct_curr_bal AS VARCHAR) FROM account WHERE acct_id = 1",
                String.class))
        .isEqualTo("0.00");
  }

  @Test
  void jobStatusEndpoint() {
    long id =
        launcher
            .launch(
                new ReportRequest(
                    ReportType.YEARLY, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31), "U"))
            .executionId();
    var ok =
        rest.withBasicAuth("USER0001", "PASSWORD").getForEntity("/api/v1/jobs/" + id, Map.class);
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ok.getBody()).containsEntry("jobName", "TRANREPT");
    var nf = rest.withBasicAuth("USER0001", "PASSWORD").getForEntity("/api/v1/jobs/1", Map.class);
    assertThat(nf.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    var anon = rest.getForEntity("/api/v1/jobs/" + id, String.class);
    assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
