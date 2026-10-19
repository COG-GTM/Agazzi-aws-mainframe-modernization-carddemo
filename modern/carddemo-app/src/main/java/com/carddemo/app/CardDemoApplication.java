package com.carddemo.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** CardDemo Spring Boot entry point: scans every workstream module under {@code com.carddemo}. */
@SpringBootApplication(scanBasePackages = "com.carddemo")
@EntityScan(basePackages = "com.carddemo")
@EnableJpaRepositories(basePackages = "com.carddemo")
public class CardDemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(CardDemoApplication.class, args);
  }
}
