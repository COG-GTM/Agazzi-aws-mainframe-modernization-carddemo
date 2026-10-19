package com.carddemo.contracts.port;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Asynchronous launch of the TRANREPT report job (CORPT00C writes JCL to TDQ JOBS -> INTRDR).
 * Consumed by O4 (CR00), implemented by B3/B5.
 */
public interface ReportJobLauncherPort {

  /** Report period selected on CORPT00 (MONTHLY / YEARLY / CUSTOM start-end). */
  enum ReportType {
    MONTHLY,
    YEARLY,
    CUSTOM
  }

  record ReportRequest(
      ReportType type, LocalDate startDate, LocalDate endDate, String requestedBy) {}

  record ReportJobHandle(long executionId, String jobName, String status) {}

  ReportJobHandle launch(ReportRequest request);

  Optional<ReportJobHandle> status(long executionId);
}
