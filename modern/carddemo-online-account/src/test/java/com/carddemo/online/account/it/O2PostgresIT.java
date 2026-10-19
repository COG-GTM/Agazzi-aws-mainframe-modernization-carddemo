package com.carddemo.online.account.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.online.account.parity.GoldenScenarios;
import com.carddemo.online.account.testapp.AccountTestApplication;
import com.carddemo.online.account.testapp.Screens;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** O2 goldens and the CAUP commit on PostgreSQL 16 with the real Flyway V1..V3 schema. */
@Tag("it")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AccountTestApplication.class)
@AutoConfigureMockMvc
class O2PostgresIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void goldensPassOnPostgres() throws Exception {
    List<String> mismatches = new ArrayList<>();
    for (String tranid : new String[] {"CAVW", "CAUP"}) {
      for (Map<String, Object> sc : GoldenScenarios.load(tranid)) {
        mismatches.addAll(GoldenScenarios.run(mvc, sc, null));
      }
    }
    assertThat(mismatches).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select cust_fico_credit_score from customer where cust_id = 1", Integer.class))
        .isEqualTo(750);
  }

  @Test
  void concurrentUpdateDetectedOnPostgres() throws Exception {
    Screens a = new Screens(mvc, "USER0001", "coactup");
    Screens b = new Screens(mvc, "ADMIN001", "coactup");
    for (Screens t : new Screens[] {a, b}) {
      t.first();
      t.send("ENTER", "ACCTSID", "00000000002");
    }
    a.send("ENTER", "ACSTFCO", "701", "ACSPH1A", "908", "ACSPH2A", "908", "ACSSTTE", "IN");
    b.send("ENTER", "ACSTFCO", "702", "ACSPH1A", "908", "ACSPH2A", "908", "ACSSTTE", "IN");
    List<String> first = Screens.messages(a.send("PF5"));
    List<String> second = Screens.messages(b.send("PF5"));
    assertThat(first).contains("Changes committed to database");
    assertThat(second).contains("Record changed by some one else. Please review");
  }
}
