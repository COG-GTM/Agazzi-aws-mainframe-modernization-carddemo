package com.carddemo.itauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-002 end to end: CC00 sign-on creates a Spring Session JDBC row (cookie HttpOnly +
 * SameSite=Strict), the COMMAREA follows the user across CM00/CA00, PF3 deletes the session.
 */
@SpringBootTest(
    classes = AuthItApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "spring.batch.job.enabled=false"
    })
@Testcontainers
class OnlineAuthSessionIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();
  private static final String CSRF = "it-csrf-token";

  private HttpResponse<String> call(String method, String map, String body, String cookie)
      throws Exception {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/screens/" + map))
            .header("Accept", "application/json");
    // Double-submit CSRF (O1 CookieCsrfTokenRepository): cookie and header carry the same token.
    String csrf = "XSRF-TOKEN=" + CSRF;
    b.header("Cookie", cookie == null ? csrf : cookie + "; " + csrf).header("X-XSRF-TOKEN", CSRF);
    if (body == null) {
      b.GET();
    } else {
      b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    }
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String signOn(String user) throws Exception {
    var r =
        call(
            "POST",
            "cosgn00",
            "{\"action\":\"ENTER\",\"fields\":{\"USERID\":\""
                + user
                + "\",\"PASSWD\":\"PASSWORD\"}}",
            null);
    assertThat(r.statusCode()).isEqualTo(200);
    String setCookie =
        r.headers().allValues("Set-Cookie").stream()
            .filter(c -> !c.startsWith("XSRF-TOKEN="))
            .findFirst()
            .orElseThrow();
    assertThat(setCookie).contains("HttpOnly").contains("SameSite=Strict");
    return setCookie.substring(0, setCookie.indexOf(';'));
  }

  private String users() {
    return jdbc.queryForList(
            "SELECT sec_usr_id || sec_usr_pwd || sec_usr_type || version FROM app_user ORDER BY 1",
            String.class)
        .toString();
  }

  @Test
  void signOnMenuAndLogoutWithServerSideSession() throws Exception {
    String before = users();
    String cookie = signOn("user0001");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = 'USER0001'",
                Integer.class))
        .isEqualTo(1);

    JsonNode menu = json.readTree(call("GET", "comen01", null, cookie).body());
    assertThat(menu.at("/data/OPTN001").asText()).isEqualTo("01. Account View");
    assertThat(menu.at("/context/userId").asText()).isEqualTo("USER0001");

    JsonNode sel =
        json.readTree(
            call("POST", "comen01", "{\"action\":\"ENTER\",\"fields\":{\"OPTION\":\"1\"}}", cookie)
                .body());
    assertThat(sel.get("nextScreen").asText()).isEqualTo("COACTVW");
    assertThat(sel.at("/context/fromProgram").asText()).isEqualTo("COMEN01C");

    assertThat(call("GET", "coadm01", null, cookie).statusCode()).isEqualTo(403);

    // back on the menu (COACTVWC PF3 = XCTL COMEN01C with PGM-CONTEXT ENTER -> first send)
    assertThat(call("GET", "comen01", null, cookie).statusCode()).isEqualTo(200);
    var pf3 = call("POST", "comen01", "{\"action\":\"PF3\",\"fields\":{}}", cookie);
    assertThat(json.readTree(pf3.body()).get("nextScreen").asText()).isEqualTo("COSGN00");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = 'USER0001'",
                Integer.class))
        .isZero();
    assertThat(call("GET", "comen01", null, cookie).statusCode()).isEqualTo(401);
    assertThat(users()).as("USRSEC is read-only for O1").isEqualTo(before);
  }

  @Test
  void adminSignOnGoesToAdminMenu() throws Exception {
    String cookie = signOn("ADMIN001");
    JsonNode m = json.readTree(call("GET", "coadm01", null, cookie).body());
    assertThat(m.at("/data/TRNNAME").asText()).isEqualTo("CA00");
    assertThat(m.at("/context/userType").asText()).isEqualTo("ADMIN");
    JsonNode x =
        json.readTree(
            call("POST", "coadm01", "{\"action\":\"ENTER\",\"fields\":{\"OPTION\":\"3\"}}", cookie)
                .body());
    assertThat(x.get("nextScreen").asText()).isEqualTo("COUSR02");
  }

  @Test
  void simultaneousSessionsDoNotShareState() throws Exception {
    List<String> ids =
        List.of("ADMIN001", "ADMIN002", "ADMIN003", "USER0001", "USER0002", "USER0003");
    ExecutorService pool = Executors.newFixedThreadPool(ids.size());
    try {
      List<Future<Boolean>> fs = new ArrayList<>();
      for (String id : ids) {
        fs.add(
            pool.submit(
                () -> {
                  String c = signOn(id);
                  String map = id.startsWith("ADMIN") ? "coadm01" : "comen01";
                  JsonNode m = json.readTree(call("GET", map, null, c).body());
                  return id.equals(m.at("/context/userId").asText());
                }));
      }
      for (Future<Boolean> f : fs) {
        assertThat(f.get()).isTrue();
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
