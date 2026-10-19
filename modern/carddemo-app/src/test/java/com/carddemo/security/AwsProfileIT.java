package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.app.CardDemoApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.Cookie;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Profile aws + aws-sm: DB credentials are read from AWS Secrets Manager (LocalStack), the app
 * connects with them, logs are JSON and the session cookie is {@code __Host-}/Secure.
 */
@SpringBootTest(
    classes = CardDemoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"aws", "aws-sm"})
@ExtendWith(OutputCaptureExtension.class)
class AwsProfileIT {

  static final String SECRET_ID = "carddemo/db";
  static final String DB_USER = "carddemo_sm";
  static final String DB_PASSWORD = UUID.randomUUID().toString();
  static final List<String> PROPS =
      List.of(
          "CARDDEMO_DB_SECRET_ID",
          "CARDDEMO_DB_URL",
          "spring.cloud.aws.region.static",
          "spring.cloud.aws.credentials.access-key",
          "spring.cloud.aws.credentials.secret-key",
          "spring.cloud.aws.secretsmanager.endpoint",
          "carddemo.messaging.sqs.endpoint");

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withUsername(DB_USER)
          .withPassword(DB_PASSWORD);
  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(
              LocalStackContainer.Service.SECRETSMANAGER, LocalStackContainer.Service.SQS);

  /** Config-data imports are resolved before DynamicPropertySource, so use system properties. */
  @BeforeAll
  static void secretInSecretsManager() {
    POSTGRES.start();
    LOCALSTACK.start();
    try (var sm =
        SecretsManagerClient.builder()
            .endpointOverride(LOCALSTACK.getEndpoint())
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
            .build()) {
      sm.createSecret(
          b ->
              b.name(SECRET_ID)
                  .secretString(
                      "{\"username\":\"" + DB_USER + "\",\"password\":\"" + DB_PASSWORD + "\"}"));
    }
    String endpoint = LOCALSTACK.getEndpoint().toString();
    System.setProperty("CARDDEMO_DB_SECRET_ID", SECRET_ID);
    System.setProperty("CARDDEMO_DB_URL", POSTGRES.getJdbcUrl());
    System.setProperty("spring.cloud.aws.region.static", LOCALSTACK.getRegion());
    System.setProperty("spring.cloud.aws.credentials.access-key", LOCALSTACK.getAccessKey());
    System.setProperty("spring.cloud.aws.credentials.secret-key", LOCALSTACK.getSecretKey());
    System.setProperty("spring.cloud.aws.secretsmanager.endpoint", endpoint);
    System.setProperty("carddemo.messaging.sqs.endpoint", endpoint);
  }

  @AfterAll
  static void cleanup() {
    PROPS.forEach(System::clearProperty);
    LOCALSTACK.stop();
    POSTGRES.stop();
  }

  @Autowired TestRestTemplate http;
  @Autowired DataSource dataSource;
  @Autowired ServerProperties server;

  @Test
  void credentialsComeFromSecretsManager() throws Exception {
    try (var c = dataSource.getConnection()) {
      assertThat(c.getMetaData().getUserName()).isEqualTo(DB_USER);
    }
    assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(http.getForEntity("/actuator/info", String.class).getStatusCode())
        .as("only health is exposed in aws")
        .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND);
  }

  @Test
  void sessionCookieHardened() {
    Cookie cookie = server.getServlet().getSession().getCookie();
    assertThat(cookie.getName()).isEqualTo("__Host-CARDDEMO_SESSION");
    assertThat(cookie.getSecure()).isTrue();
    assertThat(cookie.getHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.STRICT);
    assertThat(cookie.getPath()).isEqualTo("/");
  }

  @Test
  void hstsBehindTlsTerminatingProxy() {
    HttpHeaders h = new HttpHeaders();
    h.add("X-Forwarded-Proto", "https");
    var resp = http.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(h), String.class);
    assertThat(resp.getHeaders().getFirst("Strict-Transport-Security"))
        .isEqualTo("max-age=31536000; includeSubDomains");
  }

  @Test
  void auditTrailIsJson(CapturedOutput out) throws Exception {
    http.getForEntity("/api/v1/jobs/1", String.class);
    ObjectMapper json = new ObjectMapper();
    JsonNode audit =
        Arrays.stream(out.getOut().split("\\R"))
            .filter(l -> l.startsWith("{") && l.contains("\"carddemo.audit\""))
            .map(
                l -> {
                  try {
                    return json.readTree(l);
                  } catch (Exception e) {
                    throw new IllegalStateException(l, e);
                  }
                })
            .filter(n -> n.toString().contains("AUTHORIZATION_FAILURE"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no JSON audit line in:\n" + out.getOut()));
    assertThat(audit.get("loggerName").asText()).isEqualTo("carddemo.audit");
    assertThat(audit.get("kvpList").toString()).contains("\"outcome\":\"DENIED\"");
    assertThat(out.getOut()).doesNotContain(DB_PASSWORD);
  }
}
