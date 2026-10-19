package com.carddemo.ext.trantype.batch;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;

/** Test access to the package-private tasklets for the parity/IT packages. */
public final class TrantypeJobsConfigurationBridge {

  private TrantypeJobsConfigurationBridge() {}

  public static void maintenance(TranTypeMaintenance job, StepContribution c, ChunkContext ctx) {
    TrantypeJobsConfiguration.maintenance(job, c, ctx);
  }
}
