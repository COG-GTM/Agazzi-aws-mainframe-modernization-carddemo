package com.carddemo.app.fallback;

import com.carddemo.contracts.port.ReportJobLauncherPort;
import java.util.Optional;

/** Placeholder until B3/B5 provide the TRANREPT job: every launch is rejected explicitly. */
final class UnavailableReportJobLauncher implements ReportJobLauncherPort {

  @Override
  public ReportJobHandle launch(ReportRequest request) {
    throw new UnsupportedOperationException("TRANREPT job not available yet (B3/B5)");
  }

  @Override
  public Optional<ReportJobHandle> status(long executionId) {
    return Optional.empty();
  }
}
