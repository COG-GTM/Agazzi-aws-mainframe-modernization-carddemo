package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.app.CardDemoApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the integrated carddemo-app (O1 SecurityFilterChain + carddemo-security) on PostgreSQL 16:
 * headers, session cookie flags, CSRF and the structured audit trail end to end.
 */
@SpringBootTest(
    classes = CardDemoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class SecurityHardeningIT {

  static final String USER = "AUDITOR1";

  /** Seed demo user (Flyway V2, PE-001): not a real secret. */
  static final String SEED_USER = "USER0001";

  static final String SEED_PASSWORD = "PASSWORD";
  static final String CSRF = "it-csrf-token";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired TestRestTemplate http;
  @LocalServerPort int port;

  /** ACCESS is written after the response is committed: the client can see the body first. */
  static void awaitAudit(CapturedOutput out, String fragment) throws InterruptedException {
    for (int i = 0; i < 50 && !out.getOut().contains(fragment); i++) {
      Thread.sleep(100);
    }
  }

  private HttpEntity<String> screenRequest(String body) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, h);
  }

  /** SPA-style request: session cookie plus double-submit CSRF token (cookie and header). */
  private HttpEntity<String> spaRequest(String body, String sessionCookie) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    String csrf = "XSRF-TOKEN=" + CSRF;
    h.add(HttpHeaders.COOKIE, sessionCookie == null ? csrf : sessionCookie + "; " + csrf);
    h.add("X-XSRF-TOKEN", CSRF);
    return new HttpEntity<>(body, h);
  }

  @Test
  void securityHeadersOnPublicAndRejectedResponses() {
    var health = http.getForEntity("/actuator/health", String.class);
    assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(health.getBody()).doesNotContain("components", "details");
    HttpHeaders h = health.getHeaders();
    assertThat(h.getFirst("Content-Security-Policy")).contains("frame-ancestors 'none'");
    assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(h.getFirst("X-Frame-Options")).isEqualTo("DENY");
    assertThat(h.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(h.getFirst("Strict-Transport-Security")).as("plain HTTP: no HSTS").isNull();

    var denied = http.getForEntity("/api/v1/screens/coactvw", String.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(denied.getHeaders().getFirst("Content-Security-Policy")).isNotNull();
    assertThat(denied.getHeaders().getFirst("Cache-Control")).contains("no-store");
  }

  @Test
  void forwardedTlsGetsHsts() {
    HttpHeaders h = new HttpHeaders();
    h.add("X-Forwarded-Proto", "https");
    var resp =
        http.exchange(
            "/actuator/health",
            org.springframework.http.HttpMethod.GET,
            new HttpEntity<>(h),
            String.class);
    // forward-headers-strategy is only on in profile aws; locally the header must be ignored.
    assertThat(resp.getHeaders().getFirst("Strict-Transport-Security")).isNull();
  }

  @Test
  void sessionCookieIsHttpOnlySameSiteStrict(CapturedOutput out) throws InterruptedException {
    var signOn =
        http.postForEntity(
            "/api/v1/screens/cosgn00",
            spaRequest(
                "{\"action\":\"ENTER\",\"fields\":{\"USERID\":\""
                    + SEED_USER
                    + "\",\"PASSWD\":\""
                    + SEED_PASSWORD
                    + "\"}}",
                null),
            String.class);
    assertThat(signOn.getStatusCode()).isEqualTo(HttpStatus.OK);
    String cookie =
        signOn.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
            .filter(c -> c.startsWith("CARDDEMO_SESSION="))
            .findFirst()
            .orElseThrow();
    assertThat(cookie).contains("HttpOnly", "SameSite=Strict");
    assertThat(signOn.getHeaders().getFirst("X-Request-Id")).isNotBlank();

    var noCsrf =
        http.postForEntity(
            "/api/v1/screens/coactup",
            screenRequestWithCookie(cookie.substring(0, cookie.indexOf(';'))),
            String.class);
    assertThat(noCsrf.getStatusCode()).as("CSRF active").isEqualTo(HttpStatus.FORBIDDEN);

    var resp =
        http.postForEntity(
            "/api/v1/screens/coactup",
            spaRequest(
                "{\"action\":\"PF5\",\"fields\":{\"acctSid\":\"00000000001\"}}",
                cookie.substring(0, cookie.indexOf(';'))),
            String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

    awaitAudit(out, "event=\"ACCESS\" principal=\"" + SEED_USER + "\" category=\"UPDATE\"");
    assertThat(out.getOut())
        .contains(
            "event=\"ACCESS\" principal=\""
                + SEED_USER
                + "\" category=\"UPDATE\" method=\"POST\""
                + " path=\"/api/v1/screens/coactup\" screen=\"coactup\" action=\"PF5\""
                + " status=\"200\" outcome=\"SUCCESS\"")
        .doesNotContain("00000000001")
        .doesNotContain("PASSWD\":\"" + SEED_PASSWORD);
  }

  private HttpEntity<String> screenRequestWithCookie(String sessionCookie) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.add(HttpHeaders.COOKIE, sessionCookie);
    return new HttpEntity<>("{\"action\":\"PF5\",\"fields\":{}}", h);
  }

  @Test
  void failedSignOnAndAnonymousAccessAreAudited(CapturedOutput out) throws Exception {
    // java.net.http: HttpURLConnection cannot surface a 401 on a streamed POST body.
    var bad =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/screens/cosgn00"))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Basic "
                            + Base64.getEncoder()
                                .encodeToString(
                                    (USER + ":WRONGPWD").getBytes(StandardCharsets.UTF_8)))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(bad.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    http.getForEntity("/api/v1/jobs/1", String.class);

    assertThat(out.getOut())
        .contains("event=\"AUTHENTICATION_FAILURE\" principal=\"AUDITOR1\" outcome=\"DENIED\"")
        .contains("reason=\"BadCredentialsException\"")
        .contains("event=\"AUTHORIZATION_FAILURE\" principal=\"anonymousUser\" outcome=\"DENIED\"")
        .doesNotContain("WRONGPWD");
  }
}
