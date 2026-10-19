package com.carddemo.batch.reports.launcher;

import com.carddemo.batch.reports.support.DatasetCatalog;
import com.carddemo.batch.reports.support.DatasetSource;
import com.carddemo.batch.reports.tranrept.TranreptJobConfig;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.ReportJobLauncherPort;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

/**
 * Replaces the CORPT00C submission of TRANREPT through the internal reader: the date range is
 * computed as in CORPT00C (Monthly = current month, Yearly = current year, Custom = requested
 * dates) and TRANREPT runs against the V1 tables ({@code source=db}).
 */
public class SpringBatchReportJobLauncher implements ReportJobLauncherPort {

  private final JobLauncher launcher;
  private final Job tranrept;
  private final JobExplorer explorer;
  private final BusinessClock clock;
  private final Path datasetRoot;

  public SpringBatchReportJobLauncher(
      JobLauncher launcher, Job tranrept, JobExplorer explorer, BusinessClock clock, Path root) {
    this.launcher = launcher;
    this.tranrept = tranrept;
    this.explorer = explorer;
    this.clock = clock;
    this.datasetRoot = root;
  }

  @Override
  public ReportJobHandle launch(ReportRequest request) {
    LocalDate[] range = range(request, clock.today());
    String requestId = UUID.randomUUID().toString();
    JobParameters params =
        new JobParametersBuilder()
            .addString(DatasetCatalog.DATASET_DIR, datasetRoot.resolve(requestId).toString())
            .addString(DatasetSource.SOURCE, "db")
            .addString(TranreptJobConfig.START_DATE, range[0].toString())
            .addString(TranreptJobConfig.END_DATE, range[1].toString())
            .addString("reportType", request.type().name(), false)
            .addString("requestedBy", String.valueOf(request.requestedBy()), false)
            .addString("requestId", requestId)
            .toJobParameters();
    try {
      return handle(launcher.run(tranrept, params));
    } catch (Exception e) {
      throw new IllegalStateException("TRANREPT submission failed", e);
    }
  }

  @Override
  public Optional<ReportJobHandle> status(long executionId) {
    return Optional.ofNullable(explorer.getJobExecution(executionId))
        .map(SpringBatchReportJobLauncher::handle);
  }

  static LocalDate[] range(ReportRequest request, LocalDate today) {
    return switch (request.type()) {
      case MONTHLY ->
          new LocalDate[] {today.withDayOfMonth(1), YearMonth.from(today).atEndOfMonth()};
      case YEARLY ->
          new LocalDate[] {today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31)};
      case CUSTOM -> {
        if (request.startDate() == null || request.endDate() == null) {
          throw new IllegalArgumentException("Custom report requires start and end date");
        }
        if (request.startDate().isAfter(request.endDate())) {
          throw new IllegalArgumentException("Start date must not be after end date");
        }
        yield new LocalDate[] {request.startDate(), request.endDate()};
      }
    };
  }

  static ReportJobHandle handle(JobExecution e) {
    String status =
        e.getStatus() == BatchStatus.COMPLETED || e.getStatus() == BatchStatus.FAILED
            ? e.getExitStatus().getExitCode()
            : e.getStatus().name();
    return new ReportJobHandle(e.getId(), e.getJobInstance().getJobName(), status);
  }
}
