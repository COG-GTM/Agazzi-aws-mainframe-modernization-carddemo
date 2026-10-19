package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.repository.AccountRepository;
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
import java.util.Comparator;
import java.util.List;

@Configuration
public class PrintAccountsJobConfiguration {

    @Bean("accountReportWriter")
    @StepScope
    public FlatFileItemWriter<String> accountReportWriter(
            @Value("#{jobParameters['output']}") String output) {
        return ReportSupport.writer(Path.of(output == null ? "account-report.txt" : output));
    }

    @Bean
    public Step printAccountsStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            AccountRepository accounts,
            @Qualifier("accountReportWriter")
            FlatFileItemWriter<String> accountReportWriter) {
        return new StepBuilder("printAccountsStep", repository)
                .tasklet(new ReportTasklet<>(
                        accountReportWriter,
                        "CBACT01C",
                        () -> accounts.findAll().stream()
                                .sorted(Comparator.comparing(Account::getAcctId))
                                .toList(),
                        account -> List.of(
                                "ACCT-ID                 :" + account.getAcctId(),
                                "ACCT-ACTIVE-STATUS      :" + account.getActiveStatus(),
                                "ACCT-CURR-BAL           :"
                                        + ReportSupport.money(account.getCurrentBalance()),
                                "ACCT-CREDIT-LIMIT       :"
                                        + ReportSupport.money(account.getCreditLimit()),
                                "ACCT-CASH-CREDIT-LIMIT  :"
                                        + ReportSupport.money(account.getCashCreditLimit()),
                                "ACCT-OPEN-DATE          :" + account.getOpenDate(),
                                "ACCT-EXPIRAION-DATE     :" + account.getExpirationDate(),
                                "ACCT-REISSUE-DATE       :" + account.getReissueDate(),
                                "ACCT-CURR-CYC-CREDIT    :"
                                        + ReportSupport.money(account.getCurrentCycleCredit()),
                                "ACCT-CURR-CYC-DEBIT     :"
                                        + ReportSupport.money(account.getCurrentCycleDebit()),
                                "ACCT-GROUP-ID           :" + account.getGroupId(),
                                "-------------------------------------------------")), transactionManager)
                .build();
    }

    @Bean
    public Job printAccountsJob(
            JobRepository repository,
            @Qualifier("printAccountsStep") Step printAccountsStep) {
        return new JobBuilder("printAccountsJob", repository)
                .start(printAccountsStep)
                .build();
    }
}
