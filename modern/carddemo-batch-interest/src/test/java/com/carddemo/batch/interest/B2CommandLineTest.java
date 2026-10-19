package com.carddemo.batch.interest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** {@code java -jar … --spring.batch.job.name=<JCL> PARM=…}: process exit code = job RC. */
class B2CommandLineTest {

  @TempDir Path tmp;

  private int run(String... args) {
    String[] all = new String[args.length + 3];
    all[0] = "--spring.batch.job.enabled=true";
    all[1] =
        "--spring.datasource.url=jdbc:h2:mem:b2cli;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
            + "DB_CLOSE_DELAY=-1";
    all[2] = "--spring.main.web-application-type=none";
    System.arraycopy(args, 0, all, 3, args.length);
    try (ConfigurableApplicationContext ctx =
        new SpringApplicationBuilder(B2TestApplication.class).run(all)) {
      return SpringApplication.exit(ctx);
    }
  }

  @Test
  void jobsRunFromTheCommandLineWithJclReturnCodes() throws Exception {
    Path ps = tmp.resolve("TCATBALF.PS");
    Files.write(ps, List.of("000000000990100010000010000{0000000000000000000000"));

    assertThat(run("--spring.batch.job.name=TCATBALF", "TCATBAL=" + ps)).isZero();
    assertThat(run("--spring.batch.job.name=INTCALC", "PARM=2022071800")).isEqualTo(16);
  }
}
