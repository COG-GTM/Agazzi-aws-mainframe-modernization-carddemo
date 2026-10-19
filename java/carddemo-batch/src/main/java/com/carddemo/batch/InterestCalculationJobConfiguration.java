package com.carddemo.batch;

import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

@Configuration
public class InterestCalculationJobConfiguration {

    @Bean("interestCalculationReportWriter")
    @StepScope
    public FlatFileItemWriter<String> interestCalculationReportWriter(
            @Value("#{jobParameters['output']}") String output) {
        return ReportSupport.writer(Path.of(
                output == null ? "interest-report.txt" : output));
    }

    @Bean
    @StepScope
    public Tasklet interestCalculationTasklet(
            TranCatBalanceRepository balances,
            AccountRepository accounts,
            CardXrefRepository xrefs,
            DisclosureGroupRepository disclosures,
            TransactionRepository transactions,
            @Qualifier("interestCalculationReportWriter")
            FlatFileItemWriter<String> writer,
            @Value("#{jobParameters['interest-date']}") String interestDate) {
        return new InterestCalculationTasklet(
                balances,
                accounts,
                xrefs,
                disclosures,
                transactions,
                writer,
                interestDate);
    }

    @Bean
    public Step interestCalculationStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            @Qualifier("interestCalculationTasklet") Tasklet interestCalculationTasklet) {
        return new StepBuilder("interestCalculationStep", repository)
                .tasklet(interestCalculationTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job interestCalculationJob(
            JobRepository repository,
            @Qualifier("interestCalculationStep") Step interestCalculationStep) {
        return new JobBuilder("interestCalculationJob", repository)
                .start(interestCalculationStep)
                .build();
    }
}
