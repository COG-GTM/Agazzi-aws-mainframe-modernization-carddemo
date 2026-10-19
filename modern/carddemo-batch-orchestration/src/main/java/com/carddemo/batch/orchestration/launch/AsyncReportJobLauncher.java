package com.carddemo.batch.orchestration.launch;

import com.carddemo.contracts.port.ReportJobLauncherPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;

/**
 * CORPT00C "submit TRANREPT through the internal reader" = asynchronous launch of the report job by
 * name. The report job reads {@code startDate}/{@code endDate} (ISO dates) as JobParameters.
 */
public class AsyncReportJobLauncher implements ReportJobLauncherPort {

  private final AsyncJobSubmitter submitter;
  private final JobExplorer explorer;
  private final String jobName;

  public AsyncReportJobLauncher(AsyncJobSubmitter submitter, JobExplorer explorer, String jobName) {
    this.submitter = submitter;
    this.explorer = explorer;
    this.jobName = jobName;
  }

  @Override
  public ReportJobHandle launch(ReportRequest request) {
    JobParameters params =
        new JobParametersBuilder()
            .addString("reportType", request.type().name())
            .addString("startDate", request.startDate().toString())
            .addString("endDate", request.endDate().toString())
            .addString("requestedBy", String.valueOf(request.requestedBy()), false)
            .addString("requestId", UUID.randomUUID().toString())
            .toJobParameters();
    try {
      JobExecution e = submitter.submit(jobName, params);
      return handle(e);
    } catch (Exception e) {
      throw new IllegalStateException("cannot submit " + jobName + ": " + e.getMessage(), e);
    }
  }

  @Override
  public Optional<ReportJobHandle> status(long executionId) {
    return Optional.ofNullable(explorer.getJobExecution(executionId))
        .map(AsyncReportJobLauncher::handle);
  }

  private static ReportJobHandle handle(JobExecution e) {
    return new ReportJobHandle(e.getId(), e.getJobInstance().getJobName(), e.getStatus().name());
  }
}
