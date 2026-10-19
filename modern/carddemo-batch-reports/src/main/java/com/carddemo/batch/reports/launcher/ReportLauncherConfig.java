package com.carddemo.batch.reports.launcher;

import com.carddemo.batch.reports.tranrept.TranreptJobConfig;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.ReportJobLauncherPort;
import java.nio.file.Path;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Contributes the {@link ReportJobLauncherPort} bean (component scan), which makes the Fase 0
 * {@code UnavailableReportJobLauncher} fallback ({@code @ConditionalOnMissingBean}) back off. Jobs
 * are launched asynchronously, like the CICS submission to the internal reader.
 */
@Configuration(proxyBeanMethods = false)
public class ReportLauncherConfig {

  @Bean
  ReportJobLauncherPort reportJobLauncherPort(
      JobRepository repo,
      @Qualifier(TranreptJobConfig.JOB) Job tranrept,
      JobExplorer explorer,
      BusinessClock clock,
      @Value("${carddemo.reports.dataset-root:${java.io.tmpdir}/carddemo-reports}") String root)
      throws Exception {
    TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(repo);
    launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("tranrept-"));
    launcher.afterPropertiesSet();
    return new SpringBatchReportJobLauncher(launcher, tranrept, explorer, clock, Path.of(root));
  }
}
