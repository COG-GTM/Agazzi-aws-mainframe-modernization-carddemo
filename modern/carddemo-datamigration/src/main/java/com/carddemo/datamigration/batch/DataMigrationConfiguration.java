package com.carddemo.datamigration.batch;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.datamigration.security.UsrsecPasswordMigration;
import com.carddemo.datamigration.service.DataMigrationService;
import com.carddemo.datamigration.service.MigrationOptions;
import com.carddemo.datamigration.store.RelationalStore;
import com.carddemo.platform.time.SystemBusinessClock;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Batch jobs of the 1B data migration. Opt-in ({@code carddemo.datamigration.enabled}):
 * carddemo-app scans {@code com.carddemo} and must not get these jobs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "carddemo.datamigration", name = "enabled", havingValue = "true")
public class DataMigrationConfiguration {

  public static final String VSAM_TO_RELATIONAL = "vsamToRelational";
  public static final String RELATIONAL_TO_VSAM = "relationalToVsam";
  public static final String USRSEC_TO_BCRYPT = "usrsecToBcrypt";
  public static final String USRSEC_ROLLBACK = "usrsecRollback";
  public static final String USRSEC_PURGE_BACKUP = "usrsecPurgeBackup";
  public static final String APPLY_CONSTRAINTS = "applyConstraints";

  /** Tasklet body with access to the job execution id and options. */
  @FunctionalInterface
  interface Body {
    void run(long jobExecutionId, MigrationOptions options, StepContribution contribution);
  }

  @Bean
  @ConditionalOnMissingBean
  BusinessClock dataMigrationClock() {
    return new SystemBusinessClock();
  }

  @Bean
  RelationalStore relationalStore(DataSource dataSource) {
    return new RelationalStore(new JdbcTemplate(dataSource));
  }

  @Bean
  DataMigrationService dataMigrationService(
      RelationalStore store, PlatformTransactionManager txm, BusinessClock clock) {
    return new DataMigrationService(store, new TransactionTemplate(txm), clock);
  }

  @Bean
  UsrsecPasswordMigration usrsecPasswordMigration(
      DataSource dataSource, PlatformTransactionManager txm, BusinessClock clock) {
    return new UsrsecPasswordMigration(
        new JdbcTemplate(dataSource), new TransactionTemplate(txm), clock);
  }

  private static Step step(
      String name, JobRepository repo, PlatformTransactionManager txm, Body body) {
    return new StepBuilder(name, repo)
        .tasklet(
            (StepContribution c, ChunkContext ctx) -> {
              var exec = ctx.getStepContext().getStepExecution().getJobExecution();
              body.run(exec.getId(), MigrationOptions.from(exec.getJobParameters()), c);
              return RepeatStatus.FINISHED;
            },
            txm)
        .allowStartIfComplete(true)
        .build();
  }

  @Bean
  Job vsamToRelational(
      JobRepository repo, PlatformTransactionManager txm, DataMigrationService svc) {
    return new JobBuilder(VSAM_TO_RELATIONAL, repo)
        .incrementer(new RunIdIncrementer())
        .start(
            step(
                "vsamToRelational.decode",
                repo,
                txm,
                (id, o, c) -> {
                  var run = svc.run(id);
                  svc.extract(run, o).values().forEach(s -> c.incrementReadCount());
                }))
        .next(
            step(
                "vsamToRelational.validate",
                repo,
                txm,
                (id, o, c) -> {
                  var run = svc.run(id);
                  svc.validate(run, o);
                  long errors = svc.errorCount(run);
                  if (errors > 0 && o.onError() == MigrationOptions.OnError.ABORT) {
                    svc.reconcileFailed(run, o);
                    svc.finish(id);
                    throw new IllegalStateException(
                        errors + " validation error(s), nothing loaded: see reconciliation.md");
                  }
                }))
        .next(
            step(
                "vsamToRelational.load",
                repo,
                txm,
                (id, o, c) ->
                    svc.load(svc.run(id), o, id).values().forEach(n -> c.incrementWriteCount(n))))
        .next(
            step(
                "vsamToRelational.reconcile",
                repo,
                txm,
                (id, o, c) -> {
                  var report = svc.reconcile(svc.run(id), o, id);
                  svc.finish(id);
                  if (!report.matched()) {
                    throw new IllegalStateException(
                        "reconciliation MISMATCH: see reconciliation.md");
                  }
                }))
        .build();
  }

  @Bean
  Job relationalToVsam(
      JobRepository repo, PlatformTransactionManager txm, DataMigrationService svc) {
    return new JobBuilder(RELATIONAL_TO_VSAM, repo)
        .incrementer(new RunIdIncrementer())
        .start(
            step(
                "relationalToVsam.unload",
                repo,
                txm,
                (id, o, c) ->
                    svc.unload(svc.run(id), o).forEach(u -> c.incrementWriteCount(u.records()))))
        .next(
            step(
                "relationalToVsam.compare",
                repo,
                txm,
                (id, o, c) -> {
                  var diffs = svc.compare(svc.run(id), o);
                  svc.finish(id);
                  long bad = diffs.stream().filter(d -> !d.identical()).count();
                  if (o.verify() && bad > 0) {
                    throw new IllegalStateException(
                        bad + " file(s) differ: see relational-to-vsam.md");
                  }
                }))
        .build();
  }

  @Bean
  Job usrsecToBcrypt(
      JobRepository repo, PlatformTransactionManager txm, UsrsecPasswordMigration pwd) {
    return single(
        USRSEC_TO_BCRYPT, repo, txm, (id, o, c) -> c.incrementWriteCount(pwd.migrate().migrated()));
  }

  @Bean
  Job usrsecRollback(
      JobRepository repo, PlatformTransactionManager txm, UsrsecPasswordMigration pwd) {
    return single(
        USRSEC_ROLLBACK, repo, txm, (id, o, c) -> c.incrementWriteCount(pwd.rollback().restored()));
  }

  @Bean
  Job usrsecPurgeBackup(
      JobRepository repo, PlatformTransactionManager txm, UsrsecPasswordMigration pwd) {
    return single(
        USRSEC_PURGE_BACKUP, repo, txm, (id, o, c) -> c.incrementWriteCount(pwd.purgeBackup()));
  }

  @Bean
  Job applyConstraints(JobRepository repo, PlatformTransactionManager txm, DataSource ds) {
    return single(
        APPLY_CONSTRAINTS,
        repo,
        txm,
        (id, o, c) ->
            c.incrementWriteCount(
                Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/constraints")
                    .table("dm_constraints_history")
                    .baselineOnMigrate(true)
                    .baselineVersion("149")
                    .load()
                    .migrate()
                    .migrationsExecuted));
  }

  private static Job single(
      String name, JobRepository repo, PlatformTransactionManager txm, Body body) {
    return new JobBuilder(name, repo)
        .incrementer(new RunIdIncrementer())
        .start(step(name + ".run", repo, txm, body))
        .build();
  }
}
