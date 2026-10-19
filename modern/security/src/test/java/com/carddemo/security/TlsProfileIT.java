package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.carddemo.app.CardDemoApplication;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Profile tls: PEM bundle from the environment, TLS 1.2+/AEAD only, HSTS on every response. */
@SpringBootTest(
    classes = CardDemoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "tls"})
@Testcontainers
class TlsProfileIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static Path dir;

  /** Throwaway self-signed localhost certificate generated per run (nothing in the repo). */
  @DynamicPropertySource
  static void certificate(DynamicPropertyRegistry r) throws Exception {
    dir = Files.createTempDirectory("carddemo-tls");
    Process p =
        new ProcessBuilder(
                "openssl",
                "req",
                "-x509",
                "-newkey",
                "rsa:2048",
                "-nodes",
                "-days",
                "1",
                "-subj",
                "/CN=localhost",
                "-addext",
                "subjectAltName=DNS:localhost",
                "-keyout",
                dir.resolve("key.pem").toString(),
                "-out",
                dir.resolve("cert.pem").toString())
            .redirectErrorStream(true)
            .start();
    p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
    assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "openssl not available");
    r.add("CARDDEMO_TLS_CERT", () -> "file:" + dir.resolve("cert.pem"));
    r.add("CARDDEMO_TLS_KEY", () -> "file:" + dir.resolve("key.pem"));
  }

  @LocalServerPort int port;

  private SSLContext trusting() throws Exception {
    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(null, null);
    try (InputStream in = Files.newInputStream(dir.resolve("cert.pem"))) {
      ks.setCertificateEntry(
          "carddemo", CertificateFactory.getInstance("X.509").generateCertificate(in));
    }
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ks);
    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(null, tmf.getTrustManagers(), null);
    return ctx;
  }

  private HttpResponse<String> get(SSLParameters params) throws Exception {
    HttpClient.Builder b = HttpClient.newBuilder().sslContext(trusting());
    if (params != null) {
      b.sslParameters(params);
    }
    return b.build()
        .send(
            HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/actuator/health"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void servesTls13WithHsts() throws Exception {
    HttpResponse<String> resp = get(null);
    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(resp.sslSession())
        .hasValueSatisfying(s -> assertThat(s.getProtocol()).isEqualTo("TLSv1.3"));
    assertThat(resp.headers().firstValue("Strict-Transport-Security"))
        .hasValue("max-age=31536000; includeSubDomains");
  }

  @Test
  void rejectsNonAeadCipherSuites() {
    SSLParameters cbcOnly = new SSLParameters();
    cbcOnly.setProtocols(new String[] {"TLSv1.2"});
    cbcOnly.setCipherSuites(new String[] {"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"});
    Exception e =
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> get(cbcOnly));
    assertThat(e)
        .satisfiesAnyOf(
            x -> assertThat(x).isInstanceOf(SSLHandshakeException.class),
            x -> assertThat(x).hasRootCauseInstanceOf(SSLHandshakeException.class),
            x -> assertThat(x).isInstanceOf(java.io.IOException.class));
  }
}
