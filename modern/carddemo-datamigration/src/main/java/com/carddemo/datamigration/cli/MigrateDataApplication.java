package com.carddemo.datamigration.cli;

import com.carddemo.datamigration.batch.DataMigrationConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Boot entry of the {@code migrate-data} CLI (JDBC + Flyway + Spring Batch, no JPA/web). Guarded by
 * {@code carddemo.datamigration.cli} so that carddemo-app's {@code com.carddemo} scan skips it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "carddemo.datamigration", name = "cli", havingValue = "true")
@EnableAutoConfiguration(
    exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@Import(DataMigrationConfiguration.class)
public class MigrateDataApplication {}
