package com.carddemo.batch;

import com.carddemo.domain.Customer;
import com.carddemo.domain.repository.CustomerRepository;
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
public class PrintCustomersJobConfiguration {

    @Bean("customerReportWriter")
    @org.springframework.batch.core.configuration.annotation.StepScope
    public FlatFileItemWriter<String> customerReportWriter(
            @Value("#{jobParameters['output']}") String output) {
        return ReportSupport.writer(Path.of(output == null ? "customer-report.txt" : output));
    }

    @Bean
    public Step printCustomersStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            CustomerRepository customers,
            @Qualifier("customerReportWriter") FlatFileItemWriter<String> writer) {
        return new StepBuilder("printCustomersStep", repository)
                .tasklet(new ReportTasklet<>(
                        writer,
                        "CBCUS01C",
                        () -> customers.findAll().stream()
                                .sorted(Comparator.comparing(Customer::getCustomerId))
                                .toList(),
                        customer -> List.of(
                                "CUST-ID                 : " + customer.getCustomerId(),
                                "CUST-FIRST-NAME         : " + customer.getFirstName(),
                                "CUST-MIDDLE-NAME        : " + customer.getMiddleName(),
                                "CUST-LAST-NAME          : " + customer.getLastName(),
                                "CUST-ADDR-LINE-1        : " + customer.getAddressLine1(),
                                "CUST-ADDR-LINE-2        : " + customer.getAddressLine2(),
                                "CUST-ADDR-LINE-3        : " + customer.getAddressLine3(),
                                "CUST-ADDR-STATE-CD      : " + customer.getStateCode(),
                                "CUST-ADDR-COUNTRY-CD    : " + customer.getCountryCode(),
                                "CUST-ADDR-ZIP           : " + customer.getZip(),
                                "CUST-FICO-CREDIT-SCORE  : " + customer.getFicoCreditScore(),
                                "-------------------------------------------------")), transactionManager)
                .build();
    }

    @Bean
    public Job printCustomersJob(
            JobRepository repository,
            @Qualifier("printCustomersStep") Step printCustomersStep) {
        return new JobBuilder("printCustomersJob", repository)
                .start(printCustomersStep)
                .build();
    }
}
