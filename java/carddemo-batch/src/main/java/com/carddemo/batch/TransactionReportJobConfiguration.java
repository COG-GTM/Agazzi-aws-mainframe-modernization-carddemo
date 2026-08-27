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

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class TransactionReportJobConfiguration {

    @Bean("transactionReportWriter")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemWriter<String> transactionReportWriter(
            @Value("#{jobParameters['output']}") String output) {
        return ReportSupport.writer(Path.of(
                output == null ? "transaction-report.txt" : output));
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public Tasklet transactionReportTasklet(
            TransactionRepository transactions,
            CardXrefRepository xrefs,
            TransactionTypeRepository types,
            TransactionCategoryRepository categories,
            @Qualifier("transactionReportWriter") FlatFileItemWriter<String> writer,
            @Value("#{jobParameters['start-date']}") String startDate,
            @Value("#{jobParameters['end-date']}") String endDate) {
        return new ReportTasklet<>(
                writer,
                "CBTRN03C",
                () -> List.of(new ReportInput(
                        transactions,
                        xrefs,
                        types,
                        categories,
                        startDate == null ? "0000-01-01" : startDate,
                        endDate == null ? "9999-12-31" : endDate)),
                input -> input.lines());
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

    private record ReportInput(
            TransactionRepository transactions,
            CardXrefRepository xrefs,
            TransactionTypeRepository types,
            TransactionCategoryRepository categories,
            String startDate,
            String endDate) {

        private List<String> lines() {
            Map<String, CardXref> xrefByCard = new HashMap<>();
            for (CardXref xref : xrefs.findAll()) {
                xrefByCard.put(xref.getCardNumber(), xref);
            }
            Map<String, TransactionType> typeByCode = new HashMap<>();
            for (TransactionType type : types.findAll()) {
                typeByCode.put(type.getType(), type);
            }
            Map<TransactionCategoryId, TransactionCategory> categoryById = new HashMap<>();
            for (TransactionCategory category : categories.findAll()) {
                categoryById.put(category.getId(), category);
            }
            List<Transaction> filtered = transactions.findAll().stream()
                    .filter(transaction -> {
                        String date = transaction.getProcessingTimestamp().substring(0, 10);
                        return date.compareTo(startDate) >= 0
                                && date.compareTo(endDate) <= 0;
                    })
                    .sorted(Comparator.comparing(Transaction::getCardNumber)
                            .thenComparing(Transaction::getId))
                    .toList();
            List<String> lines = new java.util.ArrayList<>();
            lines.add("DALYREPT                             Daily Transaction Report"
                    + "        Date Range: " + startDate + " to " + endDate);
            lines.add("");
            lines.add("Transaction ID  Account ID   Transaction Type"
                    + "                   Tran Category              Tran Source"
                    + "            Amount");
            lines.add("-".repeat(133));
            String currentCard = null;
            BigDecimal accountTotal = BigDecimal.ZERO.setScale(2);
            BigDecimal pageTotal = BigDecimal.ZERO.setScale(2);
            BigDecimal grandTotal = BigDecimal.ZERO.setScale(2);
            int pageRecords = 0;
            for (Transaction transaction : filtered) {
                if (!transaction.getCardNumber().equals(currentCard)) {
                    if (currentCard != null) {
                        lines.add("Account Total" + ".".repeat(86)
                                + ReportSupport.money(accountTotal));
                    }
                    currentCard = transaction.getCardNumber();
                    accountTotal = BigDecimal.ZERO.setScale(2);
                    CardXref xref = xrefByCard.get(currentCard);
                    lines.add("CARD NUMBER: " + currentCard
                            + " ACCOUNT: " + (xref == null ? "NOT FOUND" : xref.getAccountId()));
                }
                CardXref xref = xrefByCard.get(transaction.getCardNumber());
                String typeDescription = typeByCode.containsKey(transaction.getTypeCode())
                        ? typeByCode.get(transaction.getTypeCode()).getDescription()
                        : "INVALID TRANSACTION TYPE";
                TransactionCategory category = categoryById.get(new TransactionCategoryId(
                        transaction.getTypeCode(), transaction.getCategoryCode()));
                String categoryDescription = category == null
                        ? "INVALID TRAN CATG KEY"
                        : category.getDescription();
                lines.add(String.format(
                        "%-16s %-11s %-2s %-28s %04d %-20s %10s",
                        transaction.getId(),
                        xref == null ? "NOT FOUND" : xref.getAccountId(),
                        transaction.getTypeCode(),
                        typeDescription,
                        transaction.getCategoryCode(),
                        categoryDescription,
                        ReportSupport.money(transaction.getAmount())));
                accountTotal = accountTotal.add(transaction.getAmount());
                pageTotal = pageTotal.add(transaction.getAmount());
                grandTotal = grandTotal.add(transaction.getAmount());
                pageRecords++;
                if (pageRecords == 20) {
                    lines.add("Page Total" + ".".repeat(86)
                            + ReportSupport.money(pageTotal));
                    lines.add("-".repeat(133));
                    pageTotal = BigDecimal.ZERO.setScale(2);
                    pageRecords = 0;
                }
            }
            if (currentCard != null) {
                lines.add("Account Total" + ".".repeat(86)
                        + ReportSupport.money(accountTotal));
            }
            if (pageRecords > 0) {
                lines.add("Page Total" + ".".repeat(86)
                        + ReportSupport.money(pageTotal));
            }
            lines.add("Grand Total" + ".".repeat(86)
                    + ReportSupport.money(grandTotal));
            return lines;
        }
    }
}
