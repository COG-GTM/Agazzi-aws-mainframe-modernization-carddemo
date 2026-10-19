package com.carddemo.batch.datalifecycle.job;

import com.carddemo.batch.datalifecycle.DatalifecycleJobsConfiguration;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.MaintenanceModePort;
import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.platform.files.FileSystemGenerationStore;
import com.carddemo.platform.jobs.InMemoryMaintenanceMode;
import com.carddemo.platform.time.FixedBusinessClock;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Minimal application for the job tests: B4 jobs plus the Fase 0 port implementations. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(DatalifecycleJobsConfiguration.class)
public class JobsTestApplication {

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }

  @Bean
  GenerationStore generationStore() throws IOException {
    return new FileSystemGenerationStore(Files.createTempDirectory("b4-gdg"));
  }

  @Bean
  MaintenanceModePort maintenanceMode() {
    return new InMemoryMaintenanceMode();
  }

  @Bean
  UserCredentialService userCredentialService() {
    return new UserCredentialService() {
      @Override
      public AuthenticationResult authenticate(String userId, String password) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String encode(String rawPassword) {
        return "{test}" + rawPassword;
      }
    };
  }
}
