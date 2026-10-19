package com.carddemo.batch.orchestration;

import com.carddemo.batch.orchestration.core.DatasetLocator;
import com.carddemo.batch.orchestration.core.JclSteps;
import com.carddemo.batch.orchestration.core.OrchestrationProperties;
import com.carddemo.batch.orchestration.csd.CsdCatalogTasklet;
import com.carddemo.batch.orchestration.jobs.ReproTasklet;
import com.carddemo.batch.orchestration.jobs.WaitTasklet;
import com.carddemo.batch.orchestration.launch.AsyncJobSubmitter;
import com.carddemo.batch.orchestration.launch.RegisteredJobs;
import com.carddemo.batch.orchestration.launch.SubmitJobTasklet;
import com.carddemo.batch.orchestration.maintenance.MaintenanceTasklet;
import com.carddemo.batch.orchestration.scheduler.Ca7Definitions;
import com.carddemo.batch.orchestration.scheduler.ControlMDefinitions;
import com.carddemo.batch.orchestration.scheduler.SchedulerFolder;
import com.carddemo.batch.orchestration.scheduler.SchedulerJobFactory;
import com.carddemo.batch.orchestration.scheduler.SchedulerTriggers;
import com.carddemo.batch.orchestration.transfer.FileTransferPort;
import com.carddemo.batch.orchestration.transfer.FileTransfers;
import com.carddemo.batch.orchestration.transfer.TransferTasklet;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.MaintenanceModePort;
import com.carddemo.contracts.port.WaitService;
import java.util.Set;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

/** B5 jobs: one bean per JCL member / scheduler folder, bean name = job name. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrchestrationProperties.class)
public class OrchestrationJobsConfiguration {

  @Bean
  JclSteps jclSteps(JobRepository repository, PlatformTransactionManager tx) {
    return new JclSteps(repository, tx);
  }

  @Bean
  DatasetLocator datasetLocator(OrchestrationProperties props) {
    return new DatasetLocator(props.getDatasetsDir());
  }

  @Bean
  RegisteredJobs registeredJobs(JobRegistry registry, ListableBeanFactory beans) {
    return new RegisteredJobs(registry, beans);
  }

  @Bean
  AsyncJobSubmitter asyncJobSubmitter(RegisteredJobs jobs, JobRepository repository) {
    return new AsyncJobSubmitter(jobs, repository, new SimpleAsyncTaskExecutor("intrdr-"));
  }

  @Bean
  FileTransferPort fileTransferPort(OrchestrationProperties props) {
    return FileTransfers.create(props.getTransfer());
  }

  @Bean
  SchedulerJobFactory schedulerJobFactory(
      JclSteps steps, RegisteredJobs jobs, JobLauncher launcher, BusinessClock clock) {
    return new SchedulerJobFactory(
        steps, jobs, launcher, clock, new SimpleAsyncTaskExecutor("sched-"));
  }

  private static Job single(
      JclSteps steps,
      String name,
      String step,
      org.springframework.batch.core.step.tasklet.Tasklet t) {
    return new JobBuilder(name, steps.repository())
        .incrementer(new RunIdIncrementer())
        .start(steps.step(step, t))
        .build();
  }

  @Bean("CLOSEFIL")
  Job closefil(JclSteps steps, MaintenanceModePort maintenance) {
    return single(
        steps,
        "CLOSEFIL",
        "CLCIFIL",
        new MaintenanceTasklet(maintenance, MaintenanceTasklet.Action.CLOSE));
  }

  @Bean("OPENFIL")
  Job openfil(JclSteps steps, MaintenanceModePort maintenance) {
    return single(
        steps,
        "OPENFIL",
        "OPCIFIL",
        new MaintenanceTasklet(maintenance, MaintenanceTasklet.Action.OPEN));
  }

  @Bean("WAITSTEP")
  Job waitstep(JclSteps steps, WaitService wait, OrchestrationProperties props) {
    return single(steps, "WAITSTEP", "WAIT", new WaitTasklet(wait, props.getWaitCentiseconds()));
  }

  @Bean("INTRDRJ1")
  Job intrdrj1(JclSteps steps, DatasetLocator datasets, AsyncJobSubmitter submitter) {
    return new JobBuilder("INTRDRJ1", steps.repository())
        .incrementer(new RunIdIncrementer())
        .start(
            steps.step(
                "IDCAMS",
                new ReproTasklet(
                    datasets, "AWS.M2.CARDEMO.FTP.TEST", "AWS.M2.CARDEMO.FTP.TEST.BKUP")))
        .next(steps.work("STEP01", new SubmitJobTasklet(submitter, "INTRDRJ2")))
        .build();
  }

  @Bean("INTRDRJ2")
  Job intrdrj2(JclSteps steps, DatasetLocator datasets) {
    return single(
        steps,
        "INTRDRJ2",
        "IDCAMS",
        new ReproTasklet(
            datasets, "AWS.M2.CARDEMO.FTP.TEST.BKUP", "AWS.M2.CARDEMO.FTP.TEST.BKUP.INTRDR"));
  }

  @Bean("FTPJCL")
  Job ftpjcl(
      JclSteps steps,
      FileTransferPort transfer,
      DatasetLocator datasets,
      OrchestrationProperties props) {
    return single(
        steps,
        "FTPJCL",
        "STEP1",
        new TransferTasklet(transfer, datasets, props.getTransfer().getRemoteDir()));
  }

  @Bean("CBADMCDJ")
  Job cbadmcdj(JclSteps steps, JdbcTemplate jdbc, BusinessClock clock, DatasetLocator datasets) {
    return single(steps, "CBADMCDJ", "STEP1", new CsdCatalogTasklet(jdbc, clock, datasets));
  }

  @Bean(ControlMDefinitions.DAILY)
  Job dailyFolder(SchedulerJobFactory factory) {
    return factory.build(ControlMDefinitions.daily());
  }

  @Bean(ControlMDefinitions.WEEKLY)
  Job weeklyFolder(SchedulerJobFactory factory) {
    return factory.build(ControlMDefinitions.weekly());
  }

  @Bean(ControlMDefinitions.MONTHLY)
  Job monthlyFolder(SchedulerJobFactory factory) {
    return factory.build(ControlMDefinitions.monthly());
  }

  @Bean(Ca7Definitions.POSTING)
  Job ca7Posting(SchedulerJobFactory factory) {
    return factory.build(Ca7Definitions.posting().toFolder());
  }

  @Bean(Ca7Definitions.REFRESH)
  Job ca7Refresh(SchedulerJobFactory factory) {
    return factory.build(Ca7Definitions.refresh().toFolder());
  }

  @Bean(Ca7Definitions.STATEMENTS)
  Job ca7Statements(SchedulerJobFactory factory) {
    return factory.build(Ca7Definitions.statements().toFolder());
  }

  /** {@code scripts/run_full_batch.sh} → Fase 5 cycle, jobs launched by name in order. */
  @Bean("fullBatchCycle")
  Job fullBatchCycle(SchedulerJobFactory factory, OrchestrationProperties props) {
    return factory.build(
        SchedulerFolder.chain(
            "fullBatchCycle", "fullBatchCycle", Set.of(), props.getFullCycle().getJobs()));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      prefix = "carddemo.orchestration.scheduler",
      name = "enabled",
      havingValue = "true")
  static class Triggers {
    @Bean
    SchedulerTriggers schedulerTriggers(
        JobLauncher launcher,
        BusinessClock clock,
        @Qualifier(ControlMDefinitions.DAILY) Job daily,
        @Qualifier(ControlMDefinitions.WEEKLY) Job weekly,
        @Qualifier(ControlMDefinitions.MONTHLY) Job monthly) {
      return new SchedulerTriggers(launcher, clock, daily, weekly, monthly);
    }
  }
}
