package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import com.carddemo.platform.time.FixedBusinessClock;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * CDRD/CDRA over real SQS (LocalStack) and PostgreSQL (V1 + V2 seed): the replies read from the SQS
 * reply queues must equal the GnuCOBOL golden payloads byte for byte.
 */
@Testcontainers
@SpringBootTest(
    classes = MqInquirySqsIT.ItApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "carddemo.extensions.mqinquiry.enabled=true",
      "carddemo.extensions.mqinquiry.poll-wait=1s",
      "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
class MqInquirySqsIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.SQS);

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackages = "com.carddemo.domain")
  @EnableJpaRepositories(basePackages = "com.carddemo.domain.repository")
  static class ItApplication {

    @Bean
    BusinessClock businessClock() {
      return FixedBusinessClock.golden();
    }

    @Bean(destroyMethod = "close")
    SqsClient sqsClient() {
      return SqsClient.builder()
          .endpointOverride(URI.create(LOCALSTACK.getEndpoint().toString()))
          .region(Region.of(LOCALSTACK.getRegion()))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
          .build();
    }

    @Bean
    MessageChannelPort messageChannelPort(SqsClient sqs) {
      return new TestSqsMessageChannel(sqs);
    }
  }

  @Autowired MessageChannelPort channel;
  @Autowired MqInquiryProperties properties;

  @Test
  void accountInquiryOverSqsMatchesGolden() throws Exception {
    replay("CDRA", properties.accountRequestQueue(), properties.accountReplyQueue());
  }

  @Test
  void systemDateInquiryOverSqsMatchesGolden() throws Exception {
    replay("CDRD", properties.dateRequestQueue(), properties.dateReplyQueue());
  }

  private void replay(String tranId, String requestQueue, String replyQueue) throws Exception {
    Path dir = SeedAccounts.goldenDir(tranId);
    List<String> requests =
        Files.readAllLines(dir.resolve("requests.txt"), StandardCharsets.ISO_8859_1);
    List<String> expected =
        Files.readAllLines(dir.resolve("expected-replies.txt"), StandardCharsets.ISO_8859_1);
    for (int i = 0; i < requests.size(); i++) {
      Optional<ChannelMessage> reply =
          channel.request(requestQueue, replyQueue, requests.get(i), Duration.ofSeconds(20));
      String[] golden = expected.get(i).split("\\|", -1);
      assertThat(reply).as("reply %d", i + 1).isPresent();
      assertThat(reply.get().payload()).as("payload %d", i + 1).isEqualTo(golden[2]);
      assertThat(reply.get().headers()).containsKey("requestId");
    }
    assertThat(channel.receive(properties.errorQueue(), Duration.ZERO)).isEmpty();
  }

  @Test
  void correlationIdIsPreservedAcrossSqs() {
    String id =
        channel.send(
            properties.dateRequestQueue(), "DATE", Map.of("correlationId", "CLIENT-CORR-1"));
    assertThat(id).isNotBlank();
    ChannelMessage reply = awaitReply(properties.dateReplyQueue());
    assertThat(reply.correlationId()).isEqualTo("CLIENT-CORR-1");
  }

  private ChannelMessage awaitReply(String queue) {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (System.nanoTime() < deadline) {
      Optional<ChannelMessage> m = channel.receive(queue, Duration.ofSeconds(1));
      if (m.isPresent()) {
        return m.get();
      }
    }
    throw new AssertionError("no reply on " + queue);
  }
}
