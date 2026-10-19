package com.carddemo.batch.orchestration;

import com.carddemo.batch.orchestration.core.CliExit;
import com.carddemo.batch.orchestration.core.JclExitCodeGenerator;
import com.carddemo.batch.orchestration.launch.AsyncJobSubmitter;
import com.carddemo.batch.orchestration.launch.AsyncReportJobLauncher;
import com.carddemo.batch.orchestration.maintenance.JdbcMaintenanceMode;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.MaintenanceModePort;
import com.carddemo.contracts.port.ReportJobLauncherPort;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Replaces the Fase 0 fallbacks of {@code carddemo-app} (ordered before them by name, no class
 * dependency): shared DB maintenance mode, async report launcher, JCL exit codes.
 */
@AutoConfiguration(
    after = JdbcTemplateAutoConfiguration.class,
    before = BatchAutoConfiguration.class,
    beforeName = "com.carddemo.app.fallback.Fase0FallbackPortsAutoConfiguration")
public class OrchestrationAutoConfiguration {

  @Bean
  @ConditionalOnBean(JdbcTemplate.class)
  @ConditionalOnMissingBean(MaintenanceModePort.class)
  MaintenanceModePort jdbcMaintenanceMode(JdbcTemplate jdbc, BusinessClock clock) {
    return new JdbcMaintenanceMode(jdbc, clock);
  }

  @Bean
  @ConditionalOnMissingBean(ReportJobLauncherPort.class)
  ReportJobLauncherPort asyncReportJobLauncher(AsyncJobSubmitter submitter, JobExplorer explorer) {
    return new AsyncReportJobLauncher(submitter, explorer, "TRANREPT");
  }

  @Bean
  @ConditionalOnMissingBean(ExitCodeGenerator.class)
  JclExitCodeGenerator jclExitCodeGenerator() {
    return new JclExitCodeGenerator();
  }

  @Bean
  @ConditionalOnProperty(prefix = "carddemo.orchestration.cli", name = "exit", havingValue = "true")
  CliExit cliExit() {
    return new CliExit(System::exit);
  }
}
