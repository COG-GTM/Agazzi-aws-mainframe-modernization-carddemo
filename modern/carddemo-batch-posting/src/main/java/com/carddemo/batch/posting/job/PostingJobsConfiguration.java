package com.carddemo.batch.posting.job;

import com.carddemo.batch.posting.support.PostingExitCodeGenerator;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * B1 jobs: one Spring Batch {@link Job} per JCL (bean and job name = JCL member), one step per
 * {@code EXEC} with the JCL step name. Run with {@code --spring.batch.job.name=<JCL>}.
 */
@Configuration(proxyBeanMethods = false)
public class PostingJobsConfiguration {

  public static final String POSTTRAN = "POSTTRAN";
  public static final String CBTRN01C = "CBTRN01C";
  public static final String DALYREJS = "DALYREJS";
  public static final String TRANBKP = "TRANBKP";
  public static final String COMBTRAN = "COMBTRAN";
  public static final String TRANIDX = "TRANIDX";

  public static final Set<String> JOB_NAMES =
      Set.of(POSTTRAN, CBTRN01C, DALYREJS, TRANBKP, COMBTRAN, TRANIDX);

  @Bean
  PostingDatasetsFactory postingDatasetsFactory(
      ObjectProvider<DailyTransactionRepository> dailyTransactions,
      ObjectProvider<CardXrefRepository> cardXref,
      ObjectProvider<AccountRepository> accounts,
      ObjectProvider<TranCatBalanceRepository> tranCatBalances,
      ObjectProvider<TransactionRepository> transactions,
      ObjectProvider<CustomerRepository> customers,
      ObjectProvider<CardRepository> cards) {
    return new PostingDatasetsFactory(
        dailyTransactions, cardXref, accounts, tranCatBalances, transactions, customers, cards);
  }

  @Bean
  PostingExitCodeGenerator postingExitCodeGenerator() {
    return new PostingExitCodeGenerator(JOB_NAMES);
  }

  private static Step step(
      String name, JclStepTasklet tasklet, JobRepository repo, PlatformTransactionManager tx) {
    return new StepBuilder(name, repo).tasklet(tasklet, tx).listener(tasklet).build();
  }

  @Bean(name = POSTTRAN)
  Job posttranJob(
      JobRepository repo,
      PlatformTransactionManager tx,
      PostingDatasetsFactory datasets,
      GenerationStore generations,
      BusinessClock clock) {
    return new JobBuilder(POSTTRAN, repo)
        .listener(new MaxConditionCodeListener())
        .start(
            step(
                "STEP15",
                new PostingTasklets.PosttranStep15(datasets, generations, clock),
                repo,
                tx))
        .build();
  }

  @Bean(name = CBTRN01C)
  Job cbtrn01cJob(
      JobRepository repo, PlatformTransactionManager tx, PostingDatasetsFactory datasets) {
    return new JobBuilder(CBTRN01C, repo)
        .listener(new MaxConditionCodeListener())
        .start(step("CBTRN01C", new PostingTasklets.Cbtrn01cStep(datasets), repo, tx))
        .build();
  }

  @Bean(name = DALYREJS)
  Job dalyrejsJob(JobRepository repo, PlatformTransactionManager tx, GenerationStore generations) {
    return new JobBuilder(DALYREJS, repo)
        .listener(new MaxConditionCodeListener())
        .start(step("STEP05", new PostingTasklets.DalyrejsStep05(generations), repo, tx))
        .build();
  }

  @Bean(name = TRANBKP)
  Job tranbkpJob(
      JobRepository repo,
      PlatformTransactionManager tx,
      PostingDatasetsFactory datasets,
      GenerationStore generations) {
    return new JobBuilder(TRANBKP, repo)
        .listener(new MaxConditionCodeListener())
        .start(step("STEP05R", new PostingTasklets.TranbkpStep05r(datasets, generations), repo, tx))
        .next(step("STEP05", new PostingTasklets.TranbkpStep05(datasets), repo, tx))
        .next(step("STEP10", new PostingTasklets.TranbkpStep10(datasets), repo, tx))
        .build();
  }

  @Bean(name = COMBTRAN)
  Job combtranJob(
      JobRepository repo,
      PlatformTransactionManager tx,
      PostingDatasetsFactory datasets,
      GenerationStore generations) {
    return new JobBuilder(COMBTRAN, repo)
        .listener(new MaxConditionCodeListener())
        .start(step("STEP05R", new PostingTasklets.CombtranStep05r(generations), repo, tx))
        .next(step("STEP10", new PostingTasklets.CombtranStep10(datasets, generations), repo, tx))
        .build();
  }

  @Bean(name = TRANIDX)
  Job tranidxJob(
      JobRepository repo,
      PlatformTransactionManager tx,
      PostingDatasetsFactory datasets,
      ObjectProvider<DataSource> dataSource) {
    return new JobBuilder(TRANIDX, repo)
        .listener(new MaxConditionCodeListener())
        .start(step("STEP20", new PostingTasklets.TranidxStep20(dataSource), repo, tx))
        .next(step("STEP25", new PostingTasklets.TranidxStep25(), repo, tx))
        .next(step("STEP30", new PostingTasklets.TranidxStep30(datasets), repo, tx))
        .build();
  }
}
