package com.carddemo.migration;

import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionCategoryRepository;
import com.carddemo.domain.repository.TransactionTypeRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CardDemoMigrationApplication.class,
        properties = "loader.input-dir=../../app/data/ASCII")
class CardDemoLoaderIT {

    private static final Path DATA = Path.of("..", "..", "app", "data", "ASCII");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CardRepository cards;

    @Autowired
    private CardXrefRepository xrefs;

    @Autowired
    private TransactionTypeRepository types;

    @Autowired
    private TransactionCategoryRepository categories;

    @Autowired
    private DisclosureGroupRepository disclosures;

    @Autowired
    private TranCatBalanceRepository balances;

    @Autowired
    private DailyTransactionRepository daily;

    @BeforeAll
    static void dockerAvailable() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void loadsEverySourceFile() throws Exception {
        String firstDailyTransactionId =
                SampleParsers.dailyTransactions(DATA).get(0).getId();

        assertThat(customers.count()).isEqualTo(50);
        assertThat(accounts.count()).isEqualTo(50);
        assertThat(cards.count()).isEqualTo(50);
        assertThat(xrefs.count()).isEqualTo(50);
        assertThat(types.count()).isEqualTo(7);
        assertThat(categories.count()).isEqualTo(18);
        assertThat(disclosures.count()).isEqualTo(51);
        assertThat(balances.count()).isEqualTo(50);
        assertThat(daily.count()).isEqualTo(300);
        assertThat(daily.findById(firstDailyTransactionId).orElseThrow().getAmount())
                .isEqualByComparingTo("504.77");
    }
}
