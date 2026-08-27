package com.carddemo.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

@Configuration
public class CreateStatementsJobConfiguration {

    @Bean("statementTextWriter")
    @StepScope
    public FlatFileItemWriter<String> statementTextWriter(
            @Value("#{jobParameters['statement-text']}") String output) {
        return ReportSupport.writer(Path.of(
                output == null ? "statements.txt" : output));
    }

    @Bean("statementHtmlWriter")
    @StepScope
    public FlatFileItemWriter<String> statementHtmlWriter(
            @Value("#{jobParameters['statement-html']}") String output) {
        return ReportSupport.writer(Path.of(
                output == null ? "statements.html" : output));
    }

    @Bean
    public Step createStatementsStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            StatementDataService data,
            @Qualifier("statementTextWriter") FlatFileItemWriter<String> textWriter,
            @Qualifier("statementHtmlWriter") FlatFileItemWriter<String> htmlWriter) {
        return new StepBuilder("createStatementsStep", repository)
                .tasklet(new StatementTasklet(data, textWriter, htmlWriter), transactionManager)
                .build();
    }

    @Bean
    public Job createStatementsJob(
            JobRepository repository,
            @Qualifier("createStatementsStep") Step createStatementsStep) {
        return new JobBuilder("createStatementsJob", repository)
                .start(createStatementsStep)
                .build();
    }
}
