package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.Card;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.TransactionRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Configuration
public class ReadDailyTransactionsJobConfiguration {

    @Bean("dailyReadReportWriter")
    @StepScope
    public FlatFileItemWriter<String> dailyReadReportWriter(
            @Value("#{jobParameters['output']}") String output) {
        return ReportSupport.writer(Path.of(output == null ? "daily-transaction-report.txt" : output));
    }

    @Bean
    public Step readDailyTransactionsStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            DailyTransactionRepository dailyTransactions,
            CardXrefRepository xrefs,
            AccountRepository accounts,
            CardRepository cards,
            CustomerRepository customers,
            TransactionRepository transactions,
            @Qualifier("dailyReadReportWriter") FlatFileItemWriter<String> writer) {
        return new StepBuilder("readDailyTransactionsStep", repository)
                .tasklet(new ReportTasklet<>(
                        writer,
                        "CBTRN01C",
                        () -> dailyTransactions.findAll().stream()
                                .sorted(Comparator.comparing(DailyTransaction::getId))
                                .toList(),
                        daily -> render(
                                daily,
                                xrefs,
                                accounts,
                                cards,
                                customers,
                                transactions)), transactionManager)
                .build();
    }

    @Bean
    public Job readDailyTransactionsJob(
            JobRepository repository,
            @Qualifier("readDailyTransactionsStep") Step readDailyTransactionsStep) {
        return new JobBuilder("readDailyTransactionsJob", repository)
                .start(readDailyTransactionsStep)
                .build();
    }

    private static List<String> render(
            DailyTransaction daily,
            CardXrefRepository xrefs,
            AccountRepository accounts,
            CardRepository cards,
            CustomerRepository customers,
            TransactionRepository transactions) {
        List<String> lines = new ArrayList<>();
        lines.add("DALYTRAN-ID            : " + daily.getId());
        lines.add("DALYTRAN-CARD-NUM      : " + daily.getCardNumber());
        lines.add("DALYTRAN-AMT           : " + ReportSupport.money(daily.getAmount()));
        xrefs.findById(daily.getCardNumber()).ifPresentOrElse(xref -> {
            lines.add("XREF-ACCT-ID           : " + xref.getAccountId());
            lines.add("XREF-CUST-ID           : " + xref.getCustomerId());
            accounts.findById(xref.getAccountId()).ifPresentOrElse(
                    account -> lines.add("ACCOUNT                 : " + account.getAcctId()),
                    () -> lines.add("ACCOUNT " + xref.getAccountId() + " NOT FOUND"));
            cards.findById(daily.getCardNumber()).ifPresentOrElse(
                    card -> lines.add("CARD                   : " + card.getCardNumber()),
                    () -> lines.add("CARD NUMBER " + daily.getCardNumber() + " NOT FOUND"));
            customers.findById(xref.getCustomerId()).ifPresentOrElse(
                    customer -> lines.add("CUSTOMER                : " + customer.getCustomerId()),
                    () -> lines.add("CUSTOMER " + xref.getCustomerId() + " NOT FOUND"));
        }, () -> {
            lines.add("INVALID CARD NUMBER FOR XREF");
            lines.add("CARD NUMBER " + daily.getCardNumber()
                    + " COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-"
                    + daily.getId());
        });
        transactions.findById(daily.getId()).ifPresentOrElse(
                transaction -> lines.add("TRANSACTION             : " + transaction.getId()),
                () -> lines.add("TRANSACTION " + daily.getId() + " NOT FOUND"));
        lines.add("-------------------------------------------------");
        return lines;
    }
}
