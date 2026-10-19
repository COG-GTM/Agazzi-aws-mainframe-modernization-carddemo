package com.carddemo.batch.orchestration.launch;

import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

/**
 * Parameters of a job launched by a scheduler/orchestration step: the parent's identifying
 * parameters plus the {@code scheduler.slot} that makes WAITSTEP/CLOSEFIL distinct instances when a
 * flow runs them several times. Same parent parameters ⇒ same child instance ⇒ restartable.
 */
public final class ChildJobParameters {

  public static final String SLOT = "scheduler.slot";

  private ChildJobParameters() {}

  public static JobParameters of(JobParameters parent, String slot) {
    JobParametersBuilder b = new JobParametersBuilder();
    parent
        .getParameters()
        .forEach(
            (k, v) -> {
              if (v.isIdentifying() && !SLOT.equals(k)) {
                b.addJobParameter(k, (JobParameter) v);
              }
            });
    b.addString(SLOT, slot);
    return b.toJobParameters();
  }
}
