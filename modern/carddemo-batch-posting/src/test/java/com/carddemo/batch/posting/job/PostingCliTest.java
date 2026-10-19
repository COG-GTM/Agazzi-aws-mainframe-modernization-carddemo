package com.carddemo.batch.posting.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.posting.dataset.FileDatasets;
import com.carddemo.batch.posting.golden.Golden;
import com.carddemo.batch.posting.testapp.FileStorageTestApp;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** {@code --spring.batch.job.name=<JCL>} from the command line; JVM exit code = JCL RC. */
class PostingCliTest {

  @TempDir Path dir;

  private int run(String... args) {
    ConfigurableApplicationContext ctx = SpringApplication.run(FileStorageTestApp.class, args);
    return SpringApplication.exit(ctx);
  }

  @Test
  void posttranFromCliExitsWithRc4() {
    Path in = Golden.b1("POSTTRAN-EDGE").resolve("input");
    Golden.copy(in.resolve("dailytran.txt"), dir, FileDatasets.DALYTRAN);
    Golden.copy(in.resolve("cardxref.txt"), dir, FileDatasets.CARDXREF);
    Golden.copy(in.resolve("acctdata.txt"), dir, FileDatasets.ACCTDATA);
    Golden.copy(in.resolve("tcatbal.txt"), dir, FileDatasets.TCATBALF);
    int rc =
        run(
            "--spring.batch.job.enabled=true",
            "--spring.batch.job.name=POSTTRAN",
            "--spring.datasource.url=jdbc:h2:mem:cli1;DB_CLOSE_DELAY=-1",
            "storage=file",
            "datasetDir=" + dir);
    assertThat(rc).isEqualTo(4);
  }

  @Test
  void abendFromCliExitsWith16() {
    int rc =
        run(
            "--spring.batch.job.enabled=true",
            "--spring.batch.job.name=POSTTRAN",
            "--spring.datasource.url=jdbc:h2:mem:cli2;DB_CLOSE_DELAY=-1",
            "storage=file",
            "datasetDir=" + dir);
    assertThat(rc).isEqualTo(16);
  }
}
