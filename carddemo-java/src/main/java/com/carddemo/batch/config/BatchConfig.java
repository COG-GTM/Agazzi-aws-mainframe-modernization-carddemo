package com.carddemo.batch.config;

import com.carddemo.batch.processor.TransactionPostingProcessor;
import com.carddemo.batch.processor.InterestCalculationProcessor;
import com.carddemo.entity.DailyTransaction;
import com.carddemo.entity.TransactionCategoryBalance;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch configuration replacing JCL batch jobs.
 * POSTTRAN.jcl (CBTRN02C.cbl) → transactionPostingJob
 * INTCALC.jcl (CBACT04C.cbl)  → interestCalculationJob
 */
@Configuration
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public BatchConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job transactionPostingJob(Step postTransactionStep) {
        return new JobBuilder("transactionPostingJob", jobRepository)
                .start(postTransactionStep)
                .build();
    }

    @Bean
    public Step postTransactionStep(
            ItemReader<DailyTransaction> dailyTransactionReader,
            TransactionPostingProcessor postingProcessor,
            ItemWriter<DailyTransaction> dailyTransactionWriter) {
        return new StepBuilder("postTransactionStep", jobRepository)
                .<DailyTransaction, DailyTransaction>chunk(100, transactionManager)
                .reader(dailyTransactionReader)
                .processor(postingProcessor)
                .writer(dailyTransactionWriter)
                .build();
    }

    @Bean
    public Job interestCalculationJob(Step calculateInterestStep) {
        return new JobBuilder("interestCalculationJob", jobRepository)
                .start(calculateInterestStep)
                .build();
    }

    @Bean
    public Step calculateInterestStep(
            ItemReader<TransactionCategoryBalance> tcbReader,
            InterestCalculationProcessor interestProcessor,
            ItemWriter<TransactionCategoryBalance> tcbWriter) {
        return new StepBuilder("calculateInterestStep", jobRepository)
                .<TransactionCategoryBalance, TransactionCategoryBalance>chunk(100, transactionManager)
                .reader(tcbReader)
                .processor(interestProcessor)
                .writer(tcbWriter)
                .build();
    }
}
