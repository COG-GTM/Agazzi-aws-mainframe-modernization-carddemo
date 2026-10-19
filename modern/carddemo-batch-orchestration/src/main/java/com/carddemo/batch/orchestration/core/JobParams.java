package com.carddemo.batch.orchestration.core;

import java.util.Map;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;

/** JobParameters lookup with JCL-like defaults (the value the original JCL hard-coded). */
public final class JobParams {

  private JobParams() {}

  public static String string(ChunkContext chunk, String name, String defaultValue) {
    Map<String, Object> params = chunk.getStepContext().getJobParameters();
    Object v = params.get(name);
    return v == null || v.toString().isBlank() ? defaultValue : v.toString();
  }

  public static String string(StepExecution step, String name, String defaultValue) {
    JobParameter<?> v = step.getJobParameters().getParameter(name);
    return v == null || v.getValue().toString().isBlank() ? defaultValue : v.getValue().toString();
  }
}
