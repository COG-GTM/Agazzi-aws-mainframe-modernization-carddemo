package com.carddemo.batch.interest;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.platform.files.FileSystemGenerationStore;
import com.carddemo.platform.time.FixedBusinessClock;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Test boot of the B2 module alone (in carddemo-app the Fase 0 fallbacks provide the ports). */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.carddemo.batch.interest")
@EntityScan(basePackages = "com.carddemo.domain")
@EnableJpaRepositories(basePackages = "com.carddemo.domain")
public class B2TestApplication {

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }

  @Bean
  GenerationStore generationStore() throws IOException {
    return new FileSystemGenerationStore(Files.createTempDirectory("b2-gdg"));
  }
}
