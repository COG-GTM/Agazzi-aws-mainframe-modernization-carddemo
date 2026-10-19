package com.carddemo.ext.authorization.messaging;

import com.carddemo.ext.authorization.config.AuthorizationProperties;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Background CP00 trigger: keeps a single processor loop polling the request channel. */
@Component
@ConditionalOnAuthorizationExtension
@ConditionalOnProperty(
    prefix = "carddemo.extensions.authorization",
    name = "listener-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthorizationListener implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationListener.class);

  private final AuthorizationRequestProcessor processor;
  private final AuthorizationProperties props;
  private volatile boolean running;
  private Thread worker;

  public AuthorizationListener(
      AuthorizationRequestProcessor processor, AuthorizationProperties props) {
    this.processor = processor;
    this.props = props;
  }

  @Override
  public synchronized void start() {
    running = true;
    worker =
        Thread.ofVirtual()
            .name("cp00-listener")
            .start(
                () -> {
                  while (running) {
                    try {
                      processor.runOnce(props.waitInterval());
                    } catch (RuntimeException e) {
                      LOG.error("CP00 processing failed", e);
                    }
                  }
                });
  }

  @Override
  public synchronized void stop() {
    running = false;
    if (worker != null) {
      worker.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
