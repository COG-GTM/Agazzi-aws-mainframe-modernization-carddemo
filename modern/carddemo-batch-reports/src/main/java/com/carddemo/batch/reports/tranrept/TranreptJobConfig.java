package com.carddemo.batch.reports.tranrept;

import static com.carddemo.batch.reports.config.ReportsJobSupport.step;

import com.carddemo.batch.reports.config.ReportsJobSupport;
import com.carddemo.batch.reports.support.JobAbendException;
import com.carddemo.batch.reports.support.RecordSort;
import com.carddemo.batch.reports.tranrept.Cbtrn03cService.DateParm;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * TRANREPT.jcl: {@code STEP05R} (PROC REPROC: unload TRANSACT KSDS to TRANSACT.BKUP), {@code
 * STEP05R} (SORT: date INCLUDE + sort by card number into TRANSACT.DALY), {@code STEP10R}
 * (CBTRN03C). Job parameters {@code startDate}/{@code endDate} replace the SYMNAMES and DATEPARM
 * dataset; when absent the DATEPARM dataset is used for both.
 */
@Configuration(proxyBeanMethods = false)
public class TranreptJobConfig {

  public static final String JOB = "TRANREPT";
  public static final String STEP_REPROC = "STEP05R-REPROC";
  public static final String STEP_SORT = "STEP05R-SORT";
  public static final String STEP_REPORT = "STEP10R";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";

  @Bean(name = JOB)
  Job tranreptJob(
      JobRepository repo, PlatformTransactionManager tx, ObjectProvider<JdbcTemplate> jdbc) {
    return new JobBuilder(JOB, repo)
        .listener(ReportsJobSupport.ABEND_MAPPING)
        .start(
            step(
                STEP_REPROC,
                repo,
                tx,
                jdbc,
                ctx -> {
                  var ds = ctx.datasets();
                  ds.write(
                      "TRANBKUP", List.copyOf(ds.ksds("TRANSACT", 350, 0, 16).inKeyOrder()), 350);
                }))
        .next(
            step(
                STEP_SORT,
                repo,
                tx,
                jdbc,
                ctx -> {
                  var ds = ctx.datasets();
                  DateParm range = dateParm(ctx.params(), ds.sequential("DATEPARM", 80));
                  if (range == null) {
                    throw new JobAbendException("SORT", "no date range (DATEPARM empty)");
                  }
                  ds.write(
                      "TRANDALY",
                      RecordSort.sort(
                          ds.sequential("TRANBKUP", 350),
                          List.of(
                              new RecordSort.Include(305, 10, "GE", range.startDate()),
                              new RecordSort.Include(305, 10, "LE", range.endDate())),
                          List.of(new RecordSort.Key(263, 16, true)),
                          List.of(),
                          350),
                      350);
                }))
        .next(
            step(
                STEP_REPORT,
                repo,
                tx,
                jdbc,
                ctx -> {
                  var ds = ctx.datasets();
                  DateParm range = dateParm(ctx.params(), ds.sequential("DATEPARM", 80));
                  var lookups =
                      new Cbtrn03cService.Lookups(
                          ds.ksds("CARDXREF", 50, 0, 16),
                          ds.ksds("TRANTYPE", 60, 0, 2),
                          ds.ksds("TRANCATG", 60, 0, 6));
                  List<String> report =
                      Cbtrn03cService.run(
                          ds.sequential("TRANDALY", 350),
                          lookups,
                          range == null ? null : range.toRecord());
                  ds.write("TRANREPT", report, Cbtrn03cService.REPORT_LRECL);
                }))
        .build();
  }

  static DateParm dateParm(JobParameters params, List<String> dateParmFile) {
    String start = params.getString(START_DATE);
    String end = params.getString(END_DATE);
    if (start != null && end != null) {
      return new DateParm(start, end);
    }
    return dateParmFile.isEmpty() ? null : DateParm.parse(dateParmFile.get(0));
  }
}
