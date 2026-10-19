package com.carddemo.batch.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** {@code --spring.batch.job.name=<JCL>} with job parameters as {@code key=value} arguments. */
class CommandLineJobTest {

  @TempDir Path dir;

  @Test
  void runsCreastmtThenTxt2pdf1FromCommandLine() {
    B3Fixtures.materialise("CREASTMT", dir);
    for (String job : new String[] {"CREASTMT", "TXT2PDF1"}) {
      try (ConfigurableApplicationContext ctx =
          SpringApplication.run(
              ReportsTestApplication.class,
              "--spring.batch.job.enabled=true",
              "--spring.batch.job.name=" + job,
              "--spring.datasource.url=jdbc:h2:mem:cli-" + job + ";DB_CLOSE_DELAY=-1",
              "datasetDir=" + dir)) {
        assertThat(ctx.isActive()).isTrue();
      }
    }
    assertThat(B3Fixtures.bytes(dir.resolve("STMTPS.dat")))
        .isEqualTo(B3Fixtures.bytes(B3Fixtures.B3.resolve("CREASTMT/expected/STMTPS.dat")));
    assertThat(B3Fixtures.bytes(dir.resolve("STMTPDF.dat"))).isNotEmpty();
  }
}
