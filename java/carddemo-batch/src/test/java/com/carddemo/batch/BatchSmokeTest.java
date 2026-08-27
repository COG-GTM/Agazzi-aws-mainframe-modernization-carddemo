package com.carddemo.batch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CardDemoBatchApplication.class,
        properties = {
            "spring.batch.job.enabled=false",
            "spring.datasource.url=jdbc:h2:mem:batch-smoke;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.flyway.enabled=false"
        })
class BatchSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void batchContextLoadsAllJobs() {
        assertThat(context.containsBean("postTransactionsJob")).isTrue();
        assertThat(context.containsBean("printAccountsJob")).isTrue();
        assertThat(context.containsBean("printCardsJob")).isTrue();
        assertThat(context.containsBean("printCardXrefJob")).isTrue();
        assertThat(context.containsBean("printCustomersJob")).isTrue();
        assertThat(context.containsBean("readDailyTransactionsJob")).isTrue();
        assertThat(context.containsBean("interestCalculationJob")).isTrue();
        assertThat(context.containsBean("transactionReportJob")).isTrue();
        assertThat(context.containsBean("createStatementsJob")).isTrue();
        assertThat(context.containsBean("POSTTRAN")).isTrue();
        assertThat(context.containsBean("INTCALC")).isTrue();
        assertThat(context.containsBean("CREASTMT")).isTrue();
        assertThat(context.containsBean("TRANREPT")).isTrue();
        assertThat(context.containsBean("COMBTRAN")).isTrue();
    }
}
