package com.carddemo.migration;
import com.carddemo.domain.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(classes=CardDemoMigrationApplication.class, properties="loader.input-dir=../../app/data/ASCII")
class CardDemoLoaderIT {
 static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16");
 @BeforeAll static void dockerAvailable(){Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());POSTGRES.start();}
 @AfterAll static void stopContainer(){if(POSTGRES.isRunning())POSTGRES.stop();}
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.jpa.hibernate.ddl-auto",()->"validate");}
 @Autowired CustomerRepository customers; @Autowired AccountRepository accounts; @Autowired CardRepository cards; @Autowired CardXrefRepository xrefs; @Autowired DailyTransactionRepository daily; @Autowired TranCatBalanceRepository balances; @Autowired DisclosureGroupRepository disclosures; @Autowired TransactionCategoryRepository categories; @Autowired TransactionTypeRepository types;
 @Test void loadsEverySourceFile(){assertThat(customers.count()).isEqualTo(50);assertThat(accounts.count()).isEqualTo(50);assertThat(cards.count()).isEqualTo(50);assertThat(xrefs.count()).isEqualTo(50);assertThat(types.count()).isEqualTo(7);assertThat(categories.count()).isEqualTo(18);assertThat(disclosures.count()).isEqualTo(51);assertThat(balances.count()).isEqualTo(50);assertThat(daily.count()).isEqualTo(300);assertThat(daily.findAll().get(0).getAmount()).isEqualByComparingTo("504.77");}
}
