package com.carddemo.batch.datalifecycle.job;

/** Body of one JCL step: returns the step return code or throws an abend. */
@FunctionalInterface
public interface LegacyStep {

  int execute(StepEnv env) throws Exception;
}
