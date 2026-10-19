package com.carddemo.ext.trantype.batch;

import com.carddemo.ext.trantype.TrantypeEnabled;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch jobs of X2: {@code MNTTRDB2} (job parameters {@code INPFILE}, optional {@code
 * SYSOUT}) and {@code TRANEXTR} consistency check (optional {@code TRANTYPE}, {@code TRANCATG},
 * {@code SYSOUT}). The step exit description carries the legacy return code ({@code RC=0004}).
 */
@Configuration(proxyBeanMethods = false)
@TrantypeEnabled
public class TrantypeJobsConfiguration {

  public static final String MNTTRDB2 = "MNTTRDB2";
  public static final String TRANEXTR = "TRANEXTR";

  /** Exit code for legacy RC 4 (warning); RC 8+ maps to {@link ExitStatus#FAILED}. */
  public static final String COMPLETED_WITH_WARNINGS = "COMPLETED WITH WARNINGS";

  @Bean
  Job mnttrdb2Job(JobRepository repo, PlatformTransactionManager tx, TranTypeMaintenance job) {
    Step step =
        new StepBuilder("COBTUPDT", repo).tasklet((c, ctx) -> maintenance(job, c, ctx), tx).build();
    return new JobBuilder(MNTTRDB2, repo).start(step).build();
  }

  @Bean
  Job tranextrJob(
      JobRepository repo, PlatformTransactionManager tx, TranTypeConsistencyCheck check) {
    Step step =
        new StepBuilder("CONSISTENCY", repo)
            .tasklet((c, ctx) -> consistency(check, c, ctx), tx)
            .build();
    return new JobBuilder(TRANEXTR, repo).start(step).build();
  }

  static RepeatStatus maintenance(
      TranTypeMaintenance job, StepContribution contribution, ChunkContext ctx) {
    Map<String, Object> p = ctx.getStepContext().getJobParameters();
    List<String> sysout = new ArrayList<>();
    Object in = p.get("INPFILE");
    int rc;
    Path input = in == null ? null : Path.of(in.toString());
    if (input == null || !Files.isReadable(input)) {
      rc = job.openFailed(sysout::add);
    } else {
      rc = job.run(readRecords(input, TranTypeMaintenance.RECORD_LENGTH), sysout::add);
    }
    finish(contribution, p, sysout, rc);
    return RepeatStatus.FINISHED;
  }

  static RepeatStatus consistency(
      TranTypeConsistencyCheck check, StepContribution contribution, ChunkContext ctx) {
    Map<String, Object> p = ctx.getStepContext().getJobParameters();
    TranTypeConsistencyCheck.Report r =
        check.check(optional(p, "TRANTYPE"), optional(p, "TRANCATG"));
    List<String> sysout = new ArrayList<>();
    sysout.add("TRANSACTION_TYPE ROWS     : " + r.types());
    sysout.add("TRANSACTION_CATEGORY ROWS : " + r.categories());
    sysout.addAll(r.findings());
    sysout.add(r.findings().isEmpty() ? "CONSISTENT" : "INCONSISTENCIES: " + r.findings().size());
    finish(contribution, p, sysout, r.returnCode());
    return RepeatStatus.FINISHED;
  }

  private static List<String> optional(Map<String, Object> p, String name) {
    Object v = p.get(name);
    return v == null ? null : readRecords(Path.of(v.toString()), 60);
  }

  private static void finish(
      StepContribution contribution, Map<String, Object> p, List<String> sysout, int rc) {
    Object out = p.get("SYSOUT");
    if (out != null) {
      try {
        Files.write(Path.of(out.toString()), sysout, StandardCharsets.US_ASCII);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    ExitStatus status =
        rc == 0
            ? ExitStatus.COMPLETED
            : rc < 8 ? new ExitStatus(COMPLETED_WITH_WARNINGS) : ExitStatus.FAILED;
    contribution.setExitStatus(status.addExitDescription(String.format("RC=%04d", rc)));
    if (rc >= 8) {
      throw new IllegalStateException(String.format("RC=%04d", rc));
    }
  }

  /** RECFM=F dataset (no line ends, like the GnuCOBOL harness input) or one record per line. */
  static List<String> readRecords(Path file, int lrecl) {
    try {
      String all = Files.readString(file, StandardCharsets.US_ASCII);
      if (all.indexOf('\n') >= 0) {
        return all.lines().toList();
      }
      if (all.length() % lrecl != 0) {
        throw new IllegalArgumentException(
            file
                + ": incomplete trailing record (length "
                + all.length()
                + ", LRECL "
                + lrecl
                + ")");
      }
      List<String> out = new ArrayList<>();
      for (int i = 0; i < all.length(); i += lrecl) {
        out.add(all.substring(i, Math.min(all.length(), i + lrecl)));
      }
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
