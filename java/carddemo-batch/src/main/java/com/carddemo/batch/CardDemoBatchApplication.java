package com.carddemo.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.carddemo.domain")
@EnableJpaRepositories("com.carddemo.domain.repository")
public class CardDemoBatchApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(CardDemoBatchApplication.class, args);
        int exitCode = SpringApplication.exit(context);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
