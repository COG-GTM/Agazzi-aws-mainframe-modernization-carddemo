package com.carddemo.ext.authorization.messaging;

import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import com.carddemo.ext.authorization.codec.AuthorizationReply;
import com.carddemo.ext.authorization.codec.AuthorizationRequest;
import com.carddemo.ext.authorization.config.AuthorizationProperties;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import com.carddemo.ext.authorization.service.AuthorizationDecisionService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CP00 / COPAUA0C MQ loop on {@link MessageChannelPort}: reads requests (wait interval 5 s, at most
 * 500 per run), replies on the request's {@code replyTo} channel with the request id as {@code
 * correlationId} (MQ CORRELID) and an expiry of 50 (tenths of a second).
 */
@Component
@ConditionalOnAuthorizationExtension
public class AuthorizationRequestProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationRequestProcessor.class);
  public static final int REQUESTS_PROCESS_LIMIT = 500;

  private final MessageChannelPort channel;
  private final AuthorizationDecisionService decisions;
  private final AuthorizationProperties props;

  public AuthorizationRequestProcessor(
      MessageChannelPort channel,
      AuthorizationDecisionService decisions,
      AuthorizationProperties props) {
    this.channel = channel;
    this.decisions = decisions;
    this.props = props;
  }

  /** One CP00 task run; returns the number of messages processed. */
  public int runOnce(Duration wait) {
    int processed = 0;
    Optional<ChannelMessage> m = channel.receive(props.requestChannel(), wait);
    while (m.isPresent()) {
      boolean endTask = dispatch(m.get()).endTask();
      processed++;
      if (endTask) {
        LOG.warn("CP00 task ended after critical IMS error (9990-END-ROUTINE)");
        break;
      }
      if (processed > REQUESTS_PROCESS_LIMIT) {
        break;
      }
      m = channel.receive(props.requestChannel(), wait);
    }
    return processed;
  }

  public AuthorizationReply handle(ChannelMessage msg) {
    return dispatch(msg).reply();
  }

  private AuthorizationDecisionService.Outcome dispatch(ChannelMessage msg) {
    AuthorizationDecisionService.Outcome outcome =
        decisions.decide(AuthorizationRequest.parse(msg.payload()));
    AuthorizationReply reply = outcome.reply();
    String replyTo = msg.headers().getOrDefault("replyTo", props.replyChannel());
    channel.send(
        replyTo,
        reply.toPayload(),
        Map.of("correlationId", msg.id(), "expiry", "50", "format", "MQSTR"));
    LOG.debug("CP00 {} -> {}", msg.id(), reply.authRespCode());
    return outcome;
  }
}
