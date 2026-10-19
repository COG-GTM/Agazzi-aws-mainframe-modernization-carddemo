package com.carddemo.app.messaging;

import com.carddemo.app.fallback.Fase0FallbackPortsAutoConfiguration;
import com.carddemo.contracts.port.MessageChannelPort;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * SQS adapter, active with {@code carddemo.messaging.type=sqs} (profiles local/aws). Endpoint
 * override + dummy credentials for LocalStack; the default credential chain on AWS.
 */
@AutoConfiguration(before = Fase0FallbackPortsAutoConfiguration.class)
@ConditionalOnProperty(name = "carddemo.messaging.type", havingValue = "sqs")
public class SqsMessagingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  SqsClient carddemoSqsClient(
      @Value("${carddemo.messaging.sqs.region:us-east-1}") String region,
      @Value("${carddemo.messaging.sqs.endpoint:}") String endpoint) {
    SqsClientBuilder b = SqsClient.builder().region(Region.of(region));
    if (endpoint.isBlank()) {
      b.credentialsProvider(DefaultCredentialsProvider.create());
    } else {
      b.endpointOverride(URI.create(endpoint))
          .credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
    }
    return b.build();
  }

  @Bean
  @ConditionalOnMissingBean
  MessageChannelPort sqsMessageChannel(SqsClient sqs) {
    return new SqsMessageChannel(sqs);
  }
}
