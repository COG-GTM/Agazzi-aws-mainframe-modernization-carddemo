package com.carddemo.batch.orchestration.core;

import java.util.function.IntConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/** After the CLI job ran, close the context and terminate with the job's condition code. */
public class CliExit implements ApplicationListener<ApplicationReadyEvent> {

  private final IntConsumer terminator;
  private final Runnable drain;

  /** {@code drain} waits for internally submitted (INTRDR) jobs before the JVM stops. */
  public CliExit(IntConsumer terminator, Runnable drain) {
    this.terminator = terminator;
    this.drain = drain;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    drain.run();
    terminator.accept(SpringApplication.exit(event.getApplicationContext()));
  }
}
