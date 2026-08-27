package com.carddemo.batch;

import com.carddemo.domain.CardXref;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionCategoryId;
import com.carddemo.domain.TransactionType;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.TransactionCategoryRepository;
import com.carddemo.domain.repository.TransactionRepository;
import com.carddemo.domain.repository.TransactionTypeRepository;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class TransactionReportJobConfiguration {

    @Bean("transactionReportWriter")
    @StepScope
    public FlatFileItemWriter<String> transactionReportWriter(
            @Value("#{jobParameters['output']}") String output) {
        return ReportSupport.writer(Path.of(
                output == null ? "transaction-report.txt" : output));
    }

    @Bean
    @StepScope
    public Tasklet transactionReportTasklet(
            TransactionRepository transactions,
            CardXrefRepository xrefs,
            TransactionTypeRepository types,
            TransactionCategoryRepository categories,
            @Qualifier("transactionReportWriter") FlatFileItemWriter<String> writer,
            @Value("#{jobParameters['start-date']}") String startDate,
            @Value("#{jobParameters['end-date']}") String endDate) {
        String from = startDate == null ? "0000-01-01" : startDate;
        String to = endDate == null ? "9999-12-31" : endDate;
        Map<String, CardXref> xrefsByCard = new HashMap<>();
        for (CardXref xref : xrefs.findAll()) {
            xrefsByCard.put(xref.getCardNumber(), xref);
        }
        Map<String, TransactionType> typesByCode = new HashMap<>();
        for (TransactionType type : types.findAll()) {
            typesByCode.put(type.getType(), type);
        }
        Map<TransactionCategoryId, TransactionCategory> categoriesById = new HashMap<>();
        for (TransactionCategory category : categories.findAll()) {
            categoriesById.put(category.getId(), category);
        }
        List<Transaction> filtered = transactions.findAll().stream()
                .filter(transaction -> {
                    String date = transaction.getProcessingTimestamp().substring(0, 10);
                    return date.compareTo(from) >= 0 && date.compareTo(to) <= 0;
                })
                .sorted(Comparator.comparing(Transaction::getCardNumber)
                        .thenComparing(Transaction::getId))
                .toList();
        TransactionReportRenderer renderer = new TransactionReportRenderer(
                xrefsByCard, typesByCode, categoriesById, filtered, from, to);
        return new ReportTasklet<>(
                writer,
                "CBTRN03C",
                () -> List.of(Boolean.TRUE),
                ignored -> renderer.render());
    }

    @Bean
    public Step transactionReportStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            @Qualifier("transactionReportTasklet") Tasklet transactionReportTasklet) {
        return new StepBuilder("transactionReportStep", repository)
                .tasklet(transactionReportTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job transactionReportJob(
            JobRepository repository,
            @Qualifier("transactionReportStep") Step transactionReportStep) {
        return new JobBuilder("transactionReportJob", repository)
                .start(transactionReportStep)
                .build();
    }
}
