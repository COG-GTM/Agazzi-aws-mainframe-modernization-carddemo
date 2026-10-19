package com.carddemo.ext.trantype.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.ext.trantype.batch.TranTypeConsistencyCheck;
import com.carddemo.ext.trantype.batch.TranTypeMaintenance;
import com.carddemo.ext.trantype.support.GoldenScenarios;
import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

/**
 * Gate L3 (batch) of X2: same INPFILE as the GnuCOBOL run of COBTUPDT (harness/run.sh), same SYSOUT
 * byte for byte, same RC and same TRANSACTION_TYPE content afterwards.
 */
@Tag("parity")
class Mnttrdb2GoldenParityTest {

  static final Path DIR = GoldenScenarios.ROOT.resolve("MNTTRDB2");

  @TempDir Path tmp;

  /** Runs MNTTRDB2 through the Spring Batch tasklet; returns the SYSOUT file. */
  static Path runJob(TranTypeMaintenance job, Path sysout, StepContribution[] rc) {
    StepExecution se =
        new StepExecution(
            "COBTUPDT",
            new JobExecution(
                new JobInstance(1L, "MNTTRDB2"),
                new JobParametersBuilder()
                    .addString("INPFILE", DIR.resolve("INPFILE").toString())
                    .addString("SYSOUT", sysout.toString())
                    .toJobParameters()));
    rc[0] = new StepContribution(se);
    TrantypeJobsConfigurationAccess.maintenance(job, rc[0], new ChunkContext(new StepContext(se)));
    return sysout;
  }

  @Test
  void sameSysoutReturnCodeAndTableAsCobol() throws Exception {
    InMemoryTransactionTypeStore store = InMemoryTransactionTypeStore.seeded();
    StepContribution[] c = new StepContribution[1];
    Path out = runJob(new TranTypeMaintenance(store), tmp.resolve("SYSOUT"), c);
    assertThat(Files.readAllLines(out)).isEqualTo(Files.readAllLines(DIR.resolve("SYSOUT")));
    int rc = Integer.parseInt(Files.readString(DIR.resolve("RC")).strip());
    assertThat(c[0].getExitStatus().getExitDescription()).isEqualTo(String.format("RC=%04d", rc));
    List<String> after = new ArrayList<>(new TranTypeConsistencyCheck(store).expectedTypeExtract());
    assertThat(after).isEqualTo(Files.readAllLines(DIR.resolve("TRANTYPE.after")));
  }

  /** Same-package bridge to the package-private tasklet. */
  static final class TrantypeJobsConfigurationAccess {
    static void maintenance(TranTypeMaintenance job, StepContribution c, ChunkContext ctx) {
      com.carddemo.ext.trantype.batch.TrantypeJobsConfigurationBridge.maintenance(job, c, ctx);
    }
  }
}
