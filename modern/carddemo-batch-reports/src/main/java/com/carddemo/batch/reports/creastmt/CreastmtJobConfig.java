package com.carddemo.batch.reports.creastmt;

import static com.carddemo.batch.reports.config.ReportsJobSupport.step;

import com.carddemo.batch.reports.config.ReportsJobSupport;
import com.carddemo.batch.reports.support.Ksds;
import com.carddemo.batch.reports.support.RecordSort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * CREASTMT.JCL: {@code DELDEF01} (delete TRXFL.SEQ/TRXFL KSDS), {@code STEP010} (SORT TRANSACT by
 * card + tran id, OUTREC to the COSTM01 layout), {@code STEP020} (REPRO into TRXFL KSDS, key 32),
 * {@code STEP030} (IEFBR14 delete of previous STMTPS/STMTHTML), {@code STEP040} (CBSTM03A).
 */
@Configuration(proxyBeanMethods = false)
public class CreastmtJobConfig {

  public static final String JOB = "CREASTMT";

  @Bean(name = JOB)
  Job creastmtJob(
      JobRepository repo, PlatformTransactionManager tx, ObjectProvider<JdbcTemplate> jdbc) {
    return new JobBuilder(JOB, repo)
        .listener(ReportsJobSupport.ABEND_MAPPING)
        .start(step("DELDEF01", repo, tx, jdbc, ctx -> delete(ctx, "TRXFLSEQ", "TRXFL")))
        .next(
            step(
                "STEP010",
                repo,
                tx,
                jdbc,
                ctx ->
                    ctx.datasets()
                        .write(
                            "TRXFLSEQ",
                            RecordSort.sort(
                                List.copyOf(
                                    ctx.datasets().ksds("TRANSACT", 350, 0, 16).inKeyOrder()),
                                List.of(),
                                List.of(
                                    new RecordSort.Key(263, 16, true),
                                    new RecordSort.Key(1, 16, true)),
                                List.of(
                                    new RecordSort.Outrec(1, 263, 16),
                                    new RecordSort.Outrec(17, 1, 262),
                                    new RecordSort.Outrec(279, 279, 50)),
                                350),
                            350)))
        .next(
            step(
                "STEP020",
                repo,
                tx,
                jdbc,
                ctx -> {
                  var ds = ctx.datasets();
                  Ksds trxfl = Ksds.load("TRXFL", ds.sequential("TRXFLSEQ", 350), 0, 32);
                  ds.write("TRXFL", List.copyOf(trxfl.inKeyOrder()), 350);
                }))
        .next(step("STEP030", repo, tx, jdbc, ctx -> delete(ctx, "STMTPS", "STMTHTML")))
        .next(
            step(
                "STEP040",
                repo,
                tx,
                jdbc,
                ctx -> {
                  var ds = ctx.datasets();
                  var out =
                      Cbstm03aService.run(
                          new Cbstm03aService.Files(
                              ds.sequential("TRXFL", 350),
                              List.copyOf(ds.ksds("CARDXREF", 50, 0, 16).inKeyOrder()),
                              ds.ksds("CUSTDATA", 500, 0, 9),
                              ds.ksds("ACCTDATA", 300, 0, 11)));
                  ds.write("STMTPS", out.text(), Cbstm03aService.STMT_LRECL);
                  ds.write("STMTHTML", out.html(), Cbstm03aService.HTML_LRECL);
                }))
        .build();
  }

  private static void delete(ReportsJobSupport.StepContext ctx, String... dds) {
    for (String dd : dds) {
      try {
        Files.deleteIfExists(ctx.datasets().catalog().path(dd));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
