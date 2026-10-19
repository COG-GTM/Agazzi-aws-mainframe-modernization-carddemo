package com.carddemo.ext.authorization.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.ext.authorization.X1Fixtures;
import com.carddemo.ext.authorization.messaging.AuthorizationRequestProcessor;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/** CP00 request/reply over LocalStack SQS (ADR-003 mapping: queue = channel, attrs = headers). */
@SpringBootTest
@Testcontainers
class X1SqsIT {

  @Container
  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.SQS);

  @TestConfiguration
  static class Sqs {
    @Bean
    @Primary
    MessageChannelPort sqsChannel() {
      SqsClient sqs =
          SqsClient.builder()
              .endpointOverride(LOCALSTACK.getEndpoint())
              .region(Region.of(LOCALSTACK.getRegion()))
              .credentialsProvider(
                  StaticCredentialsProvider.create(
                      AwsBasicCredentials.create(
                          LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
              .build();
      return new LocalSqsChannel(sqs);
    }
  }

  /** Same wire mapping as carddemo-app SqsMessageChannel (not a dependency of X1). */
  static final class LocalSqsChannel implements MessageChannelPort {
    private final SqsClient sqs;
    private final Map<String, String> urls = new ConcurrentHashMap<>();

    LocalSqsChannel(SqsClient sqs) {
      this.sqs = sqs;
    }

    private String url(String c) {
      return urls.computeIfAbsent(c, q -> sqs.createQueue(b -> b.queueName(q)).queueUrl());
    }

    @Override
    public String send(String channel, String payload, Map<String, String> headers) {
      Map<String, MessageAttributeValue> attrs = new HashMap<>();
      headers.forEach(
          (k, v) ->
              attrs.put(
                  k, MessageAttributeValue.builder().dataType("String").stringValue(v).build()));
      return sqs.sendMessage(
              b -> b.queueUrl(url(channel)).messageBody(payload).messageAttributes(attrs))
          .messageId();
    }

    @Override
    public Optional<ChannelMessage> receive(String channel, Duration wait) {
      int seconds = (int) Math.min(20, Math.max(0, wait.toSeconds()));
      var resp =
          sqs.receiveMessage(
              b ->
                  b.queueUrl(url(channel))
                      .maxNumberOfMessages(1)
                      .waitTimeSeconds(seconds)
                      .messageAttributeNames("All"));
      if (!resp.hasMessages() || resp.messages().isEmpty()) {
        return Optional.empty();
      }
      Message m = resp.messages().get(0);
      sqs.deleteMessage(b -> b.queueUrl(url(channel)).receiptHandle(m.receiptHandle()));
      Map<String, String> h = new HashMap<>();
      m.messageAttributes().forEach((k, v) -> h.put(k, v.stringValue()));
      return Optional.of(
          new ChannelMessage(
              m.messageId(), h.getOrDefault("correlationId", m.messageId()), m.body(), h));
    }
  }

  @Autowired MessageChannelPort channel;
  @Autowired AuthorizationRequestProcessor processor;
  @Autowired AccountRepository accounts;
  @Autowired CustomerRepository customers;
  @Autowired CardXrefRepository xrefs;

  @Test
  void cp00RepliesOnSqsWithCorrelationId() {
    accounts.save(X1Fixtures.account(80000000001L, "1000.00", "0.00"));
    customers.save(X1Fixtures.customer(800000001));
    xrefs.save(X1Fixtures.xref("4888888888888888", 800000001, 80000000001L));
    String ok =
        channel.send(
            "carddemo-auth-request",
            X1Fixtures.request("4888888888888888", "25.00", "SQS000000000001"),
            Map.of("replyTo", "carddemo-auth-reply"));
    String ko =
        channel.send(
            "carddemo-auth-request",
            X1Fixtures.request("4000000000000002", "25.00", "SQS000000000002"),
            Map.of("replyTo", "carddemo-auth-reply"));
    assertThat(processor.runOnce(Duration.ofSeconds(2))).isEqualTo(2);

    Map<String, MessageChannelPort.ChannelMessage> replies = new HashMap<>();
    for (int i = 0; i < 2; i++) {
      channel
          .receive("carddemo-auth-reply", Duration.ofSeconds(5))
          .ifPresent(m -> replies.put(m.correlationId(), m));
    }
    assertThat(replies).containsKeys(ok, ko);
    assertThat(replies.get(ok).payload()).contains(",00,0000,");
    assertThat(replies.get(ok).headers()).containsEntry("expiry", "50");
    assertThat(replies.get(ko).payload()).contains(",05,3100,");
  }
}
