package com.carddemo.batch;

import com.carddemo.domain.CardXref;
import com.carddemo.domain.repository.CardXrefRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
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
import java.util.Comparator;
import java.util.List;

@Configuration
public class PrintCardXrefJobConfiguration {

    @Bean("cardXrefReportWriter")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemWriter<String> cardXrefReportWriter(
            @Value("#{jobParameters['output']}") String output) {
        return ReportSupport.writer(Path.of(output == null ? "card-xref-report.txt" : output));
    }

    @Bean
    public Step printCardXrefStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            CardXrefRepository xrefs,
            @Qualifier("cardXrefReportWriter") FlatFileItemWriter<String> writer) {
        return new StepBuilder("printCardXrefStep", repository)
                .tasklet(new ReportTasklet<>(
                        writer,
                        "CBACT03C",
                        () -> xrefs.findAll().stream()
                                .sorted(Comparator.comparing(CardXref::getCardNumber))
                                .toList(),
                        xref -> List.of(
                                "XREF-CARD-NUM          : " + xref.getCardNumber(),
                                "XREF-CUST-ID           : " + xref.getCustomerId(),
                                "XREF-ACCT-ID           : " + xref.getAccountId(),
                                "-------------------------------------------------")), transactionManager)
                .build();
    }

    @Bean
    public Job printCardXrefJob(
            JobRepository repository,
            @Qualifier("printCardXrefStep") Step printCardXrefStep) {
        return new JobBuilder("printCardXrefJob", repository)
                .start(printCardXrefStep)
                .build();
    }
}
