package com.carddemo.ext.trantype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.domain.repository.TransactionTypeRepository;
import com.carddemo.ext.trantype.batch.TranTypeMaintenance;
import com.carddemo.ext.trantype.menu.TrantypeAdminMenu;
import com.carddemo.ext.trantype.online.TrantypeScreenController;
import com.carddemo.platform.time.FixedBusinessClock;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Flag OFF: no X2 bean (base app unchanged); flag ON: endpoints, jobs and CA00 options 5/6. */
class TrantypeFeatureFlagTest {

  @ComponentScan(
      basePackages = "com.carddemo.ext.trantype",
      excludeFilters =
          @ComponentScan.Filter(
              type = FilterType.REGEX,
              pattern = "com\\.carddemo\\.ext\\.trantype\\.(it|support|parity)\\..*"))
  static class Scan {
    @Bean
    NamedParameterJdbcTemplate jdbc() {
      return mock(NamedParameterJdbcTemplate.class);
    }

    @Bean
    TransactionTypeRepository repo() {
      return mock(TransactionTypeRepository.class);
    }

    @Bean
    BusinessClock clock() {
      return FixedBusinessClock.golden();
    }

    @Bean
    JobRepository jobRepository() {
      return mock(JobRepository.class);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Scan.class);

  @Test
  void offByDefault() {
    runner.run(
        ctx -> {
          assertThat(ctx).doesNotHaveBean(TrantypeScreenController.class);
          assertThat(ctx).doesNotHaveBean(TrantypeAdminMenu.class);
          assertThat(ctx).doesNotHaveBean(TranTypeMaintenance.class);
          assertThat(ctx).doesNotHaveBean(Job.class);
        });
    runner
        .withPropertyValues(TrantypeEnabled.PROPERTY + "=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(TrantypeScreenController.class));
  }

  @Test
  void onExposesScreensJobsAndMenuOptions() {
    runner
        .withPropertyValues(TrantypeEnabled.PROPERTY + "=true")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(TrantypeScreenController.class);
              assertThat(ctx.getBeansOfType(Job.class)).hasSize(2);
              assertThat(ctx.getBean(TrantypeAdminMenu.class).options())
                  .extracting(o -> o.number() + " " + o.program())
                  .containsExactly("5 COTRTLIC", "6 COTRTUPC");
            });
  }
}
