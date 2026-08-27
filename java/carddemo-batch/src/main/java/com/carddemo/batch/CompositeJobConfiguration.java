package com.carddemo.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CompositeJobConfiguration {

    @Bean
    public Step statementPreparationStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager) {
        return noOpStep("statementPreparationStep", repository, transactionManager,
                "CREASTMT IDCAMS delete/define is represented by writer replacement.");
    }

    @Bean
    public Step statementSortStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager) {
        return noOpStep("statementSortStep", repository, transactionManager,
                "CREASTMT SORT is represented by relational card and transaction ordering.");
    }

    @Bean
    public Step statementReplayStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager) {
        return noOpStep("statementReplayStep", repository, transactionManager,
                "CREASTMT IDCAMS REPRO is represented by repository reads.");
    }

    @Bean
    public Step statementDeleteArtifactsStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager) {
        return noOpStep("statementDeleteArtifactsStep", repository, transactionManager,
                "CREASTMT IEFBR14 is represented by FlatFileItemWriter replacement.");
    }

    @Bean
    public Step transactionBackupStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager) {
        return noOpStep("transactionBackupStep", repository, transactionManager,
                "TRANREPT REPROC backup is represented by the relational transaction table.");
    }

    @Bean
    public Step transactionSortStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager) {
        return noOpStep("transactionSortStep", repository, transactionManager,
                "TRANREPT SORT is represented by query ordering and string date filtering.");
    }

    @Bean
    public Step combineSortStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager) {
        return noOpStep("combineSortStep", repository, transactionManager,
                "COMBTRAN SORT is represented by transaction-ID ordering.");
    }

    @Bean
    public Step combineLoadStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager) {
        return noOpStep("combineLoadStep", repository, transactionManager,
                "COMBTRAN IDCAMS REPRO is unnecessary because the table is the master.");
    }

    private Step noOpStep(
            String name,
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            String message) {
        Tasklet tasklet = (contribution, context) -> {
            org.slf4j.LoggerFactory.getLogger(CompositeJobConfiguration.class)
                    .debug(message);
                return RepeatStatus.FINISHED;
        };
        return new StepBuilder(name, repository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean(name = "POSTTRAN")
    public Job postTranCompositeJob(
            JobRepository repository,
            @Qualifier("postTransactionsStep") Step postTransactionsStep,
            @Qualifier("postTransactionsJobListener")
            JobExecutionListener postTransactionsJobListener) {
        return new JobBuilder("POSTTRAN", repository)
                .listener(postTransactionsJobListener)
                .start(postTransactionsStep)
                .build();
    }

    @Bean(name = "INTCALC")
    public Job interestCompositeJob(
            JobRepository repository,
            @Qualifier("interestCalculationStep") Step interestCalculationStep) {
        return new JobBuilder("INTCALC", repository)
                .start(interestCalculationStep)
                .build();
    }

    @Bean(name = "CREASTMT")
    public Job statementCompositeJob(
            JobRepository repository,
            @Qualifier("statementPreparationStep") Step statementPreparationStep,
            @Qualifier("statementSortStep") Step statementSortStep,
            @Qualifier("statementReplayStep") Step statementReplayStep,
            @Qualifier("statementDeleteArtifactsStep") Step statementDeleteArtifactsStep,
            @Qualifier("createStatementsStep") Step createStatementsStep) {
        return new JobBuilder("CREASTMT", repository)
                .start(statementPreparationStep)
                .next(statementSortStep)
                .next(statementReplayStep)
                .next(statementDeleteArtifactsStep)
                .next(createStatementsStep)
                .build();
    }

    @Bean(name = "TRANREPT")
    public Job transactionReportCompositeJob(
            JobRepository repository,
            @Qualifier("transactionBackupStep") Step transactionBackupStep,
            @Qualifier("transactionSortStep") Step transactionSortStep,
            @Qualifier("transactionReportStep") Step transactionReportStep) {
        return new JobBuilder("TRANREPT", repository)
                .start(transactionBackupStep)
                .next(transactionSortStep)
                .next(transactionReportStep)
                .build();
    }

    @Bean(name = "COMBTRAN")
    public Job combineTransactionJob(
            JobRepository repository,
            @Qualifier("combineSortStep") Step combineSortStep,
            @Qualifier("combineLoadStep") Step combineLoadStep) {
        return new JobBuilder("COMBTRAN", repository)
                .start(combineSortStep)
                .next(combineLoadStep)
                .build();
    }
}
