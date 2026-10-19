package com.carddemo.batch.reports.pdf;

import static com.carddemo.batch.reports.config.ReportsJobSupport.step;

import com.carddemo.batch.reports.config.ReportsJobSupport;
import com.carddemo.batch.reports.creastmt.Cbstm03aService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** TXT2PDF1.JCL: step {@code TXT2PDF} converts STMTPS (INDD) to STMTPDF (STATEMNT.PS.PDF). */
@Configuration(proxyBeanMethods = false)
public class Txt2pdf1JobConfig {

  public static final String JOB = "TXT2PDF1";

  @Bean(name = JOB)
  Job txt2pdf1Job(
      JobRepository repo, PlatformTransactionManager tx, ObjectProvider<JdbcTemplate> jdbc) {
    return new JobBuilder(JOB, repo)
        .listener(ReportsJobSupport.ABEND_MAPPING)
        .start(
            step(
                "TXT2PDF",
                repo,
                tx,
                jdbc,
                ctx -> {
                  var ds = ctx.datasets();
                  byte[] pdf =
                      TextToPdf.render(ds.sequential("STMTPS", Cbstm03aService.STMT_LRECL));
                  Path out = ds.catalog().path("STMTPDF");
                  try {
                    Files.write(out, pdf);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                }))
        .build();
  }
}
