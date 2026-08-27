package com.carddemo.batch;

import com.carddemo.domain.Card;
import com.carddemo.domain.repository.CardRepository;
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
public class PrintCardsJobConfiguration {

    @Bean("cardReportWriter")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemWriter<String> cardReportWriter(
            @Value("#{jobParameters['output']}") String output) {
        return ReportSupport.writer(Path.of(output == null ? "card-report.txt" : output));
    }

    @Bean
    public Step printCardsStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            CardRepository cards,
            @Qualifier("cardReportWriter") FlatFileItemWriter<String> writer) {
        return new StepBuilder("printCardsStep", repository)
                .tasklet(new ReportTasklet<>(
                        writer,
                        "CBACT02C",
                        () -> cards.findAll().stream()
                                .sorted(Comparator.comparing(Card::getCardNumber))
                                .toList(),
                        card -> List.of(
                                "CARD-NUMBER             : " + card.getCardNumber(),
                                "CARD-ACCT-ID            : " + card.getAccountId(),
                                "CARD-CVV-CD             : " + card.getCvvCode(),
                                "CARD-EMBOSSED-NAME      : " + card.getEmbossedName(),
                                "CARD-EXPIRAION-DATE     : " + card.getExpirationDate(),
                                "CARD-ACTIVE-STATUS      : " + card.getActiveStatus(),
                                "-------------------------------------------------")), transactionManager)
                .build();
    }

    @Bean
    public Job printCardsJob(
            JobRepository repository,
            @Qualifier("printCardsStep") Step printCardsStep) {
        return new JobBuilder("printCardsJob", repository)
                .start(printCardsStep)
                .build();
    }
}
