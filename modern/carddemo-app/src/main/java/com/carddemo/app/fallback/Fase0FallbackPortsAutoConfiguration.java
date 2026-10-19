package com.carddemo.app.fallback;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.MaintenanceModePort;
import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.contracts.port.ReportJobLauncherPort;
import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.port.WaitService;
import com.carddemo.domain.repository.AppUserRepository;
import com.carddemo.platform.files.FileSystemGenerationStore;
import com.carddemo.platform.jobs.InMemoryMaintenanceMode;
import com.carddemo.platform.messaging.InMemoryMessageChannel;
import com.carddemo.platform.time.FixedBusinessClock;
import com.carddemo.platform.time.SystemBusinessClock;
import com.carddemo.platform.wait.SleepingWaitService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Fase 0 reference implementations of every port in carddemo-contracts. Registered as an
 * auto-configuration, so any bean of the same type contributed by a workstream module (component
 * scan) wins automatically and no workstream ever needs to edit carddemo-app.
 */
@AutoConfiguration
public class Fase0FallbackPortsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  BusinessClock businessClock(@Value("${carddemo.clock.fixed:}") String fixed) {
    return fixed.isBlank()
        ? new SystemBusinessClock()
        : new FixedBusinessClock(LocalDateTime.parse(fixed));
  }

  @Bean
  @ConditionalOnMissingBean
  WaitService waitService() {
    return new SleepingWaitService();
  }

  @Bean
  @ConditionalOnMissingBean
  GenerationStore generationStore(
      @Value("${carddemo.gdg.root:${java.io.tmpdir}/carddemo-gdg}") String root) {
    return new FileSystemGenerationStore(Path.of(root));
  }

  @Bean
  @ConditionalOnMissingBean
  MaintenanceModePort maintenanceModePort() {
    return new InMemoryMaintenanceMode();
  }

  @Bean
  @ConditionalOnMissingBean
  MessageChannelPort messageChannelPort() {
    return new InMemoryMessageChannel();
  }

  @Bean
  @ConditionalOnMissingBean
  ReportJobLauncherPort reportJobLauncherPort() {
    return new UnavailableReportJobLauncher();
  }

  @Bean
  @ConditionalOnMissingBean
  UserCredentialService userCredentialService(AppUserRepository users) {
    return new LegacyUserCredentialService(users);
  }
}
