package com.carddemo.batch.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** {@code --spring.batch.job.name=<JCL>} runs the job and yields the JCL condition code. */
class CliLaunchTest {

  private static int submit(String... args) {
    String[] all = new String[args.length + 1];
    System.arraycopy(args, 0, all, 0, args.length);
    all[args.length] = "--carddemo.orchestration.cli.exit=false";
    ConfigurableApplicationContext ctx = SpringApplication.run(TestApp.class, all);
    return SpringApplication.exit(ctx);
  }

  @Test
  void waitstepFromTheCommandLineEndsRc0() {
    assertThat(submit("--spring.batch.job.name=WAITSTEP", "centiseconds=00000001")).isZero();
  }

  @Test
  void failingJobFromTheCommandLineEndsWithItsReturnCode() {
    assertThat(submit("--spring.batch.job.name=CLOSEFIL", "files=NOT A FILE")).isEqualTo(8);
  }
}
