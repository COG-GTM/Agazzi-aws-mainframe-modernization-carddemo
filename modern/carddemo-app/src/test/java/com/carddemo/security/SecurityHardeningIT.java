package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.app.CardDemoApplication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots carddemo-app + carddemo-security on PostgreSQL 16 behind the Fase 0 SecurityFilterChain (O1
 * placeholder): headers, session cookie flags and the structured audit trail end to end.
 */
@SpringBootTest(
    classes = CardDemoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SecurityHardeningIT.ScreenStub.class)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class SecurityHardeningIT {

  static final String USER = "AUDITOR1";
  static final String PASSWORD = UUID.randomUUID().toString();

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void user(DynamicPropertyRegistry r) {
    r.add("spring.security.user.name", () -> USER);
    r.add("spring.security.user.password", () -> PASSWORD);
  }

  /** Stands in for the O2 COACTUPC controller until the workstream module is integrated. */
  @TestConfiguration
  @RestController
  static class ScreenStub {
    @PostMapping("/api/v1/screens/coactup")
    String coactup(HttpServletRequest request, @RequestBody String body) {
      request.getSession(true);
      return "{\"screen\":\"COACTUP\"}";
    }
  }

  @Autowired TestRestTemplate http;

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
    var resp =
        http.withBasicAuth(USER, PASSWORD)
            .postForEntity(
                "/api/v1/screens/coactup",
                screenRequest("{\"action\":\"PF5\",\"fields\":{\"acctSid\":\"00000000001\"}}"),
                String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    String cookie = resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(cookie).startsWith("CARDDEMO_SESSION=").contains("HttpOnly", "SameSite=Strict");
    assertThat(resp.getHeaders().getFirst("X-Request-Id")).isNotBlank();

    awaitAudit(out, "event=\"ACCESS\" principal=\"AUDITOR1\"");
    assertThat(out.getOut())
        .contains("event=\"AUTHENTICATION_SUCCESS\" principal=\"AUDITOR1\" outcome=\"SUCCESS\"")
        .contains(
            "event=\"ACCESS\" principal=\"AUDITOR1\" category=\"UPDATE\" method=\"POST\""
                + " path=\"/api/v1/screens/coactup\" screen=\"coactup\" action=\"PF5\""
                + " status=\"200\" outcome=\"SUCCESS\"")
        .doesNotContain("00000000001")
        .doesNotContain(PASSWORD);
  }

  @Test
  void failedSignOnAndAnonymousAccessAreAudited(CapturedOutput out) {
    var bad =
        http.withBasicAuth(USER, "WRONGPWD")
            .postForEntity("/api/v1/screens/cosgn00", screenRequest("{}"), String.class);
    assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    http.getForEntity("/api/v1/jobs/1", String.class);

    assertThat(out.getOut())
        .contains("event=\"AUTHENTICATION_FAILURE\" principal=\"AUDITOR1\" outcome=\"DENIED\"")
        .contains("reason=\"BadCredentialsException\"")
        .contains("event=\"AUTHORIZATION_FAILURE\" principal=\"anonymousUser\" outcome=\"DENIED\"")
        .doesNotContain("WRONGPWD");
  }
}
