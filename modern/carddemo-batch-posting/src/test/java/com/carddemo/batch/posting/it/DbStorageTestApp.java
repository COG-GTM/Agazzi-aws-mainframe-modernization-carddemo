package com.carddemo.batch.posting.it;

import com.carddemo.batch.posting.job.PostingJobsConfiguration;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.platform.files.FileSystemGenerationStore;
import com.carddemo.platform.time.FixedBusinessClock;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** B1 jobs on the contracts-v1 PostgreSQL schema (Flyway V1-V3 + seed V2). */
@SpringBootApplication(scanBasePackageClasses = PostingJobsConfiguration.class)
@EntityScan(basePackageClasses = Account.class)
@EnableJpaRepositories(basePackageClasses = AccountRepository.class)
public class DbStorageTestApp {

  @Bean
  GenerationStore generationStore() throws IOException {
    return new FileSystemGenerationStore(Files.createTempDirectory("b1-gdg-it"));
  }

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }
}
