package com.carddemo.batch.orchestration.core;

import java.util.function.IntConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/** After the CLI job ran, close the context and terminate with the job's condition code. */
public class CliExit implements ApplicationListener<ApplicationReadyEvent> {

  private final IntConsumer terminator;

  public CliExit(IntConsumer terminator) {
    this.terminator = terminator;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    terminator.accept(SpringApplication.exit(event.getApplicationContext()));
  }
}
