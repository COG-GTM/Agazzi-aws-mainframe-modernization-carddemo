package com.carddemo.batch.posting.testapp;

import com.carddemo.batch.posting.job.PostingJobsConfiguration;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.platform.files.FileSystemGenerationStore;
import com.carddemo.platform.time.FixedBusinessClock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** B1 jobs on file storage, Spring Batch metadata on in-memory H2 (no JPA). */
@SpringBootApplication(
    scanBasePackageClasses = PostingJobsConfiguration.class,
    exclude = {
      HibernateJpaAutoConfiguration.class,
      JpaRepositoriesAutoConfiguration.class,
      FlywayAutoConfiguration.class
    })
public class FileStorageTestApp {

  public static final Path GDG_ROOT = tempDir();

  private static Path tempDir() {
    try {
      return Files.createTempDirectory("b1-gdg");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Bean
  GenerationStore generationStore() {
    return new FileSystemGenerationStore(GDG_ROOT);
  }

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }
}
