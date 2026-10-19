package com.carddemo.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.WaitService;
import com.carddemo.platform.time.FixedBusinessClock;
import com.carddemo.platform.time.ProductionBusinessClock;
import com.carddemo.platform.wait.VirtualThreadWaitService;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformTimeAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PlatformTimeAutoConfiguration.class));

  @Test
  void contributesProductionBeans() {
    runner
        .withPropertyValues("carddemo.clock.zone=America/Chicago")
        .run(
            ctx -> {
              assertThat(ctx.getBean(BusinessClock.class))
                  .isInstanceOf(ProductionBusinessClock.class);
              assertThat(((ProductionBusinessClock) ctx.getBean(BusinessClock.class)).zone())
                  .isEqualTo(ZoneId.of("America/Chicago"));
              assertThat(ctx.getBean(WaitService.class))
                  .isInstanceOf(VirtualThreadWaitService.class);
            });
  }

  @Test
  void fixedClockPropertyKeepsTheGoldenClock() {
    runner
        .withPropertyValues("carddemo.clock.fixed=2022-07-18T12:00:00")
        .run(
            ctx ->
                assertThat(ctx.getBean(BusinessClock.class).cobolTimestamp())
                    .isEqualTo("2022-07-18-12.00.00.000000"));
  }

  @Test
  void backsOffWhenTheApplicationDeclaresItsOwnBeans() {
    runner
        .withBean(BusinessClock.class, FixedBusinessClock::golden)
        .run(
            ctx ->
                assertThat(ctx.getBean(BusinessClock.class))
                    .isInstanceOf(FixedBusinessClock.class));
  }

  @Test
  void isRegisteredAsAutoConfiguration() throws Exception {
    try (var in =
        getClass()
            .getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
      assertThat(new String(in.readAllBytes()))
          .contains(PlatformTimeAutoConfiguration.class.getName());
    }
  }
}
