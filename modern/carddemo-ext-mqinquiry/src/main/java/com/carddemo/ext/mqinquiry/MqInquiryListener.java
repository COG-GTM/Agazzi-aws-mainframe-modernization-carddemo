package com.carddemo.ext.mqinquiry;

import com.carddemo.contracts.port.MessageChannelPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Replaces the CICS MQ trigger monitor (CKTI) starting CDRD/CDRA: one virtual-thread poller per
 * request queue, each running the legacy GET/PROCESS/PUT loop.
 */
public final class MqInquiryListener implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(MqInquiryListener.class);
  private static final Duration BACKOFF = Duration.ofSeconds(1);

  private final MessageChannelPort channel;
  private final MqInquiryProcessor processor;
  private final MqInquiryProperties properties;
  private final SystemDateInquiry systemDate;
  private final AccountInquiry account;
  private final List<Thread> pollers = new ArrayList<>();
  private volatile boolean running;

  public MqInquiryListener(
      MessageChannelPort channel,
      MqInquiryProcessor processor,
      MqInquiryProperties properties,
      SystemDateInquiry systemDate,
      AccountInquiry account) {
    this.channel = channel;
    this.processor = processor;
    this.properties = properties;
    this.systemDate = systemDate;
    this.account = account;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    pollers.add(
        poller(
            "mqinquiry-CDRD",
            properties.dateRequestQueue(),
            properties.dateReplyQueue(),
            systemDate::process));
    pollers.add(
        poller(
            "mqinquiry-CDRA",
            properties.accountRequestQueue(),
            properties.accountReplyQueue(),
            account::process));
  }

  @Override
  public synchronized void stop() {
    running = false;
    pollers.forEach(Thread::interrupt);
    for (Thread t : pollers) {
      try {
        t.join(properties.pollWait().toMillis() + BACKOFF.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    pollers.clear();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public boolean isAutoStartup() {
    return properties.listenerEnabled();
  }

  private Thread poller(
      String name, String inputQueue, String replyQueue, Function<String, InquiryOutcome> program) {
    return Thread.ofVirtual()
        .name(name)
        .start(
            () -> {
              while (running) {
                try {
                  channel
                      .receive(inputQueue, properties.pollWait())
                      .ifPresent(m -> processor.handle(m, inputQueue, replyQueue, program));
                } catch (RuntimeException e) {
                  LOG.warn("{} poll failed on {}: {}", name, inputQueue, e.toString());
                  pause();
                }
              }
            });
  }

  private void pause() {
    try {
      Thread.sleep(BACKOFF);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
