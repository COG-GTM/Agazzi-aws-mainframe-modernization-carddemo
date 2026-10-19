package com.carddemo.batch.orchestration.launch;

import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

/**
 * Parameters of a job launched by a scheduler/orchestration step: the parent's identifying
 * parameters plus the {@code scheduler.slot} that makes WAITSTEP/CLOSEFIL distinct instances when a
 * flow runs them several times. Same parent parameters ⇒ same child instance ⇒ restartable.
 * Non-identifying parent parameters are forwarded as non-identifying runtime inputs.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class ChildJobParameters {

  public static final String SLOT = "scheduler.slot";

  private ChildJobParameters() {}

  public static JobParameters of(JobParameters parent, String slot) {
    return of(parent, slot, java.util.Set.of());
  }

  /**
   * Same, without the parent's own dataset overrides ({@code excluded}) that mean nothing to the
   * child.
   */
  public static JobParameters of(
      JobParameters parent, String slot, java.util.Set<String> excluded) {
    JobParametersBuilder b = new JobParametersBuilder();
    parent
        .getParameters()
        .forEach(
            (k, v) -> {
              if (!SLOT.equals(k) && !excluded.contains(k)) {
                b.addJobParameter(
                    k, new JobParameter(v.getValue(), (Class) v.getType(), v.isIdentifying()));
              }
            });
    b.addString(SLOT, slot);
    return b.toJobParameters();
  }
}
