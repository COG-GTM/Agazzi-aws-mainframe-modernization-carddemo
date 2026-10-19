package com.carddemo.batch.orchestration.core;

import org.springframework.batch.core.StepExecution;

/** Body of a step that must run outside any transaction (it launches other jobs). */
@FunctionalInterface
public interface StepWork {
  void run(StepExecution step) throws Exception;
}
