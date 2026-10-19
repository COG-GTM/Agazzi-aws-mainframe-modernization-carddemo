package com.carddemo.online.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.domain.entity.Customer;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.online.account.service.AccountUpdateWriter;
import com.carddemo.online.account.testapp.AccountTestApplication;
import com.carddemo.online.account.testapp.AccountTestApplication.RewriteHook;
import com.carddemo.online.account.testapp.Screens;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/** CAUP end-to-end through the screen resource: commit, conflicts, @Version, failures, authz. */
@SpringBootTest(
    classes = AccountTestApplication.class,
    properties = "spring.datasource.url=jdbc:h2:mem:o2flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class CoactupFlowTest {

  private static final String ACCT = "00000000001";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired RewriteHook hook;
  @Autowired PlatformTransactionManager txm;
  @Autowired AccountUpdateWriter writer;
  @Autowired CustomerRepository customers;

  @org.springframework.beans.factory.annotation.Value("${spring.datasource.url}")
  String jdbcUrl;

  @org.springframework.beans.factory.annotation.Value("${spring.datasource.username:sa}")
  String jdbcUser;

  @org.springframework.beans.factory.annotation.Value("${spring.datasource.password:}")
  String jdbcPassword;

  @AfterEach
  void resetHook() {
    hook.action = null;
  }

  private Screens validated(String user, String fico) throws Exception {
    Screens t = new Screens(mvc, user, "coactup");
    assertThat(Screens.messages(t.first())).contains("Enter or update id of account to update");
    assertThat(Screens.messages(t.send("ENTER", "ACCTSID", ACCT)))
        .contains("Update account details presented above.");
    JsonNode r =
        t.send(
            "ENTER",
            "ACSTFCO",
            fico,
            "ACSSTTE",
            "NY",
            "ACSPH2A",
            "908",
            "ACRDLIM",
            fico + ",000.50");
    assertThat(Screens.messages(r)).contains("Changes validated.Press F5 to save");
    assertThat(r.path("data").path("FKEY05").asText()).isEqualTo("F5=Save");
    assertThat(r.path("data").path("ACRDLIM").asText())
        .isEqualTo(
            com.carddemo.online.account.support.CobolText.formatAmount(
                new BigDecimal(fico + "000.50")));
    return t;
  }

  private int fico() {
    return jdbc.queryForObject(
        "select cust_fico_credit_score from customer where cust_id=1", Integer.class);
  }

  @Test
  void commitUpdatesAccountAndCustomerAtomically() throws Exception {
    long accVersion =
        jdbc.queryForObject("select version from account where acct_id=1", Long.class);
    long custVersion =
        jdbc.queryForObject("select version from customer where cust_id=1", Long.class);
    Screens t = validated("USER0001", "701");
    JsonNode r = t.send("PF5");
    assertThat(Screens.messages(r)).contains("Changes committed to database");
    assertThat(fico()).isEqualTo(701);
    assertThat(
            jdbc.queryForObject(
                "select acct_credit_limit from account where acct_id=1", BigDecimal.class))
        .isEqualByComparingTo("701000.50");
    assertThat(
            jdbc.queryForObject(
                "select cust_addr_state_cd from customer where cust_id=1", String.class))
        .isEqualTo("NY");
    assertThat(
            jdbc.queryForObject(
                "select cust_phone_num_2 from customer where cust_id=1", String.class))
        .isEqualTo("(908)693-8684  ");
    assertThat(jdbc.queryForObject("select version from account where acct_id=1", Long.class))
        .isEqualTo(accVersion + 1);
    assertThat(jdbc.queryForObject("select version from customer where cust_id=1", Long.class))
        .isEqualTo(custVersion + 1);
    assertThat(Screens.messages(t.send("ENTER")))
        .contains("Enter or update id of account to update");
  }

  @Test
  void concurrentTerminalSeesRecordChanged() throws Exception {
    Screens a = validated("USER0001", "702");
    Screens b = validated("ADMIN001", "703");
    assertThat(Screens.messages(a.send("PF5"))).contains("Changes committed to database");
    JsonNode r = b.send("PF5");
    assertThat(Screens.messages(r))
        .contains(
            "Record changed by some one else. Please review",
            "Update account details presented above.");
    assertThat(fico()).isEqualTo(702);
  }

  @Test
  void readUpdateLocksBothRowsUntilRewrite() throws Exception {
    Screens t = validated("USER0001", "704");
    List<String> blocked = new java.util.concurrent.CopyOnWriteArrayList<>();
    hook.action =
        id -> {
          // a second terminal on its own raw connection (not pooled: a lock timeout
          // makes Hikari evict the connection), outside the CAUP unit of work
          try (java.sql.Connection other =
                  java.sql.DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
              java.sql.Statement st = other.createStatement()) {
            st.execute("SET LOCK_TIMEOUT 100");
            for (String table : new String[] {"customer", "account"}) {
              String key = "customer".equals(table) ? "cust_id" : "acct_id";
              try {
                st.executeUpdate(
                    "update " + table + " set version = version where " + key + " = 1");
              } catch (java.sql.SQLException e) {
                blocked.add(table);
              }
            }
          } catch (java.sql.SQLException e) {
            blocked.add("connection failed: " + e.getMessage());
          }
        };
    assertThat(Screens.messages(t.send("PF5"))).contains("Changes committed to database");
    assertThat(blocked).containsExactly("customer", "account");
    assertThat(fico()).isEqualTo(704);
  }

  @Test
  void staleCopyElsewhereFailsOnVersion() throws Exception {
    Customer stale = customers.findById(1).orElseThrow();
    Screens t = validated("USER0001", "707");
    assertThat(Screens.messages(t.send("PF5"))).contains("Changes committed to database");
    stale.setCustFicoCreditScore(300);
    assertThatThrownBy(() -> customers.save(stale))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(fico()).isEqualTo(707);
  }

  @Test
  void fieldsAreProtectedWhileConfirmationIsPending() throws Exception {
    Screens t = validated("USER0001", "706");
    JsonNode r = t.send("PF5", "ACSTFCO", "999", "ACSFNAM", "Mallory");
    assertThat(Screens.messages(r)).contains("Changes committed to database");
    assertThat(fico()).isEqualTo(706);
    assertThat(
            jdbc.queryForObject(
                "select cust_first_name from customer where cust_id=1", String.class))
        .startsWith("Immanuel");
  }

  @Test
  void rewriteFailureIsReportedAndResetsOnNextEnter() throws Exception {
    Screens t = validated("USER0001", "705");
    hook.action =
        id -> {
          throw new IllegalStateException("simulated REWRITE failure");
        };
    JsonNode r = t.send("PF5");
    assertThat(Screens.messages(r))
        .contains("Update of record failed", "Changes unsuccessful. Please try again");
    assertThat(Screens.messages(t.send("ENTER")))
        .contains("Enter or update id of account to update");
  }

  @Test
  void writerLockErrors() {
    assertThat(writer.write(99L, 1, null, java.util.Map.of(), java.util.Map.of()))
        .isEqualTo(AccountUpdateWriter.Result.COULD_NOT_LOCK_ACCT);
    assertThat(writer.write(1L, 999_999, null, java.util.Map.of(), java.util.Map.of()))
        .isEqualTo(AccountUpdateWriter.Result.COULD_NOT_LOCK_CUST);
  }

  @Test
  void customerMissingOnFetch() throws Exception {
    jdbc.update(
        "insert into card_xref (xref_card_num, xref_cust_id, xref_acct_id, filler, version) values ('9999999999999999', 999999, 77, '              ', 0)");
    jdbc.update(
        "insert into account (acct_id, acct_active_status, acct_curr_bal, acct_credit_limit,"
            + " acct_cash_credit_limit, acct_open_date, acct_expiration_date, acct_reissue_date,"
            + " acct_curr_cyc_credit, acct_curr_cyc_debit, acct_addr_zip, acct_group_id, filler, version)"
            + " select 77, acct_active_status, acct_curr_bal, acct_credit_limit, acct_cash_credit_limit,"
            + " acct_open_date, acct_expiration_date, acct_reissue_date, acct_curr_cyc_credit,"
            + " acct_curr_cyc_debit, acct_addr_zip, acct_group_id, filler, 0 from account where acct_id = 2");
    Screens up = new Screens(mvc, "USER0001", "coactup");
    up.first();
    assertThat(Screens.messages(up.send("ENTER", "ACCTSID", "00000000077")))
        .contains("CustId:000999999 not found in customer master.Resp: 000000013  REAS:0000000");
    Screens vw = new Screens(mvc, "USER0001", "coactvw");
    vw.first();
    JsonNode r = vw.send("ENTER", "ACCTSID", "00000000077");
    assertThat(r.path("data").path("ACURBAL").asText()).isEqualTo("+        158.00");
    Screens noAcct = new Screens(mvc, "USER0001", "coactvw");
    jdbc.update(
        "insert into card_xref (xref_card_num, xref_cust_id, xref_acct_id, filler, version) values ('9999999999999998', 1, 78, '              ', 0)");
    noAcct.first();
    assertThat(Screens.messages(noAcct.send("ENTER", "ACCTSID", "00000000078")))
        .contains("Account:00000000078 not found in Acct Master file.Resp:000000013  Reas:0000");
  }

  @Test
  void anonymousIsRejectedAndRolesBehaveLikeCobol() throws Exception {
    mvc.perform(get("/api/v1/screens/coactup")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/screens/coactvw")).andExpect(status().isUnauthorized());
    // COACTUPC/COACTVWC have no SEC-USR-TYPE or ownership check: a regular user reads and
    // updates any account exactly like an admin (PARITY-EXCEPTION proposed in the PR).
    Screens user = new Screens(mvc, "USER0001", "coactvw");
    user.first();
    JsonNode r = user.send("ENTER", "ACCTSID", "00000000002");
    assertThat(r.path("fieldErrors").size()).isZero();
    JsonNode exit = user.send("PF3");
    // XCTL back to CDEMO-FROM-PROGRAM (the sign-on program for a fresh session)
    assertThat(exit.path("nextScreen").asText()).isEqualTo("COSGN00");
    assertThat(exit.path("context").path("userType").asText()).isEqualTo("USER");
    Screens admin = new Screens(mvc, "ADMIN001", "coactup");
    admin.first();
    assertThat(admin.send("ENTER", "ACCTSID", "00000000002").path("fieldErrors").size()).isZero();
    JsonNode adminExit = admin.send("PF3");
    assertThat(adminExit.path("nextScreen").isNull()).isFalse();
    assertThat(adminExit.path("context").path("fromProgram").asText()).isEqualTo("COACTUPC");
  }

  @Test
  void sessionWithoutPseudoConversationStartsOver() throws Exception {
    Screens t = new Screens(mvc, "USER0001", "coactup");
    assertThat(Screens.messages(t.send("ENTER", "ACCTSID", ACCT)))
        .contains("Enter or update id of account to update");
    assertThat(Screens.messages(t.send("PF12", "ACCTSID", ACCT)))
        .contains("Update account details presented above.");
    JsonNode bad = t.send("ENTER", "ACSTTUS", "", "ACSFNAM", "");
    assertThat(bad.path("data").path("ACSTTUS").asText()).isEqualTo("*");
    assertThat(bad.path("data").path("ACSFNAM").asText()).isEqualTo("*");
    assertThat(bad.path("data").path("FKEY12").asText()).isEqualTo("F12=Cancel");
    JsonNode reread = t.send("PF12");
    assertThat(reread.path("data").path("ACSTTUS").asText()).isEqualTo("Y");
  }
}
