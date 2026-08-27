package com.carddemo.batch;

import com.carddemo.domain.DailyTransaction;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class PostTransactionsJobConfiguration {

    @Bean
    @StepScope
    public JpaPagingItemReader<DailyTransaction> dailyTransactionReader(
            EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<DailyTransaction>()
                .name("dailyTransactionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select d from DailyTransaction d order by d.id")
                .pageSize(50)
                .build();
    }

    @Bean
    public Step postTransactionsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<DailyTransaction> dailyTransactionReader,
            DailyTransactionValidationProcessor processor,
            PostTransactionsItemWriter writer) {
        return new StepBuilder("postTransactionsStep", jobRepository)
                .<DailyTransaction, ValidatedDailyTransaction>chunk(50, transactionManager)
                .reader(dailyTransactionReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job postTransactionsJob(
            JobRepository jobRepository,
            Step postTransactionsStep,
            JobExecutionListener listener) {
        return new JobBuilder("postTransactionsJob", jobRepository)
                .listener(listener)
                .start(postTransactionsStep)
                .build();
    }

    @Bean
    public JobExecutionListener postTransactionsJobListener() {
        return new PostTransactionsJobListener();
    }
}
