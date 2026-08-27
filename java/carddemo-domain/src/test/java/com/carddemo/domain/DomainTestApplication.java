package com.carddemo.domain;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
@SpringBootApplication @EntityScan("com.carddemo.domain") @EnableJpaRepositories("com.carddemo.domain.repository")
public class DomainTestApplication {}
