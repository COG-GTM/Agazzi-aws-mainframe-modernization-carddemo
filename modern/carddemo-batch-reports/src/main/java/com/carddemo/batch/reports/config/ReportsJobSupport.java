package com.carddemo.batch.reports.config;

import com.carddemo.batch.reports.support.AbendExitStatus;
import com.carddemo.batch.reports.support.DatasetSource;
import java.util.function.Consumer;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Builds one tasklet step per JCL step, with the abend → exit status mapping. */
public final class ReportsJobSupport {

  public static final AbendExitStatus ABEND_MAPPING = new AbendExitStatus();

  private ReportsJobSupport() {}

  public static Step step(
      String name,
      JobRepository repo,
      PlatformTransactionManager tx,
      ObjectProvider<JdbcTemplate> jdbc,
      Consumer<StepContext> body) {
    return new StepBuilder(name, repo)
        .tasklet(
            (contribution, chunk) -> {
              JobParameters params =
                  contribution.getStepExecution().getJobExecution().getJobParameters();
              body.accept(
                  new StepContext(params, DatasetSource.from(params, jdbc.getIfAvailable())));
              return RepeatStatus.FINISHED;
            },
            tx)
        .listener(ABEND_MAPPING)
        .build();
  }

  /** Job parameters and datasets visible to a step. */
  public record StepContext(JobParameters params, DatasetSource datasets) {}
}
