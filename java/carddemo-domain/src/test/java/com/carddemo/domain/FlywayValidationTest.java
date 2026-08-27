package com.carddemo.domain;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(classes=DomainTestApplication.class, properties={
 "spring.datasource.url=jdbc:h2:mem:validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true"})
class FlywayValidationTest { @Test void flywaySchemaMatchesEntities(){} }
