package com.carddemo.app.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class SqsMessageChannelTest {

  @SuppressWarnings("unchecked")
  private static SqsClient sqsWithOneMessage() {
    SqsClient sqs = mock(SqsClient.class);
    when(sqs.createQueue(any(Consumer.class)))
        .thenReturn(CreateQueueResponse.builder().queueUrl("http://q/IN").build());
    when(sqs.receiveMessage(any(Consumer.class)))
        .thenReturn(
            ReceiveMessageResponse.builder()
                .messages(Message.builder().messageId("m1").receiptHandle("r1").body("x").build())
                .build());
    return sqs;
  }

  @Test
  @SuppressWarnings("unchecked")
  void receiveUnacknowledgedKeepsTheMessageUntilAcknowledge() {
    SqsClient sqs = sqsWithOneMessage();
    SqsMessageChannel channel = new SqsMessageChannel(sqs);

    ChannelMessage m = channel.receiveUnacknowledged("IN", Duration.ZERO).orElseThrow();
    verify(sqs, never()).deleteMessage(any(Consumer.class));

    channel.acknowledge("IN", m);
    channel.acknowledge("IN", m);
    verify(sqs, times(1)).deleteMessage(any(Consumer.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void receiveAcknowledgesImmediately() {
    SqsClient sqs = sqsWithOneMessage();

    assertThat(new SqsMessageChannel(sqs).receive("IN", Duration.ZERO)).isPresent();
    verify(sqs, times(1)).deleteMessage(any(Consumer.class));
  }
}
