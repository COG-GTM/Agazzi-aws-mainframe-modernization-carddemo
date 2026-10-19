package com.carddemo.batch.interest.idcams;

import com.carddemo.batch.interest.support.B2JobListener;
import com.carddemo.batch.interest.support.B2StepListener;
import com.carddemo.batch.interest.support.Sysout;
import com.carddemo.contracts.record.DisclosureGroupRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.copybook.CoreCodecs;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * app/jcl/TCATBALF.jcl and app/jcl/DISCGRP.jcl: STEP05 DELETE, STEP10 DEFINE, STEP15 REPRO of
 * {@code TCATBALF.PS} / {@code DISCGRP.PS} (job parameters {@code TCATBAL} / {@code DISCGRP}).
 */
@Configuration
public class VsamReloadJobConfig {

  public static final String TCATBALF_JOB = "TCATBALF";
  public static final String DISCGRP_JOB = "DISCGRP";
  public static final String TCATBAL_PARAMETER = "TCATBAL";
  public static final String DISCGRP_PARAMETER = "DISCGRP";

  static final String TCATBAL_TABLE = "tran_cat_balance";
  static final String DISCGRP_TABLE = "disclosure_group";

  private final JobRepository repository;
  private final PlatformTransactionManager transactionManager;
  private final JdbcTemplate jdbc;
  private final Sysout sysout;
  private final B2StepListener stepListener;

  public VsamReloadJobConfig(
      JobRepository repository,
      PlatformTransactionManager transactionManager,
      JdbcTemplate jdbc,
      Sysout sysout,
      B2StepListener stepListener) {
    this.repository = repository;
    this.transactionManager = transactionManager;
    this.jdbc = jdbc;
    this.sysout = sysout;
    this.stepListener = stepListener;
  }

  private Step step(String name, Tasklet tasklet) {
    return new StepBuilder(name, repository)
        .tasklet(tasklet, transactionManager)
        .listener(stepListener)
        .build();
  }

  private Job job(String name, B2JobListener listener, String table, Tasklet repro) {
    return new JobBuilder(name, repository)
        .incrementer(new RunIdIncrementer())
        .listener(listener)
        .start(step("STEP05", ReproTasklets.deleteCluster(jdbc, table, sysout)))
        .next(step("STEP10", ReproTasklets.defineCluster(jdbc, table, sysout)))
        .next(step("STEP15", repro))
        .build();
  }

  @Bean
  Job tcatbalfJob(B2JobListener listener) {
    return job(
        TCATBALF_JOB,
        listener,
        TCATBAL_TABLE,
        ReproTasklets.repro(
            TCATBAL_PARAMETER,
            CoreCodecs.TRAN_CAT_BALANCE,
            jdbc,
            "INSERT INTO tran_cat_balance (trancat_acct_id, trancat_type_cd, trancat_cd,"
                + " tran_cat_bal, filler) VALUES (?, ?, ?, ?, ?)",
            (TranCatBalanceRecord r) ->
                new Object[] {
                  r.trancatAcctId(), r.trancatTypeCd(), r.trancatCd(), r.tranCatBal(), r.filler()
                },
            sysout));
  }

  @Bean
  Job discgrpJob(B2JobListener listener) {
    return job(
        DISCGRP_JOB,
        listener,
        DISCGRP_TABLE,
        ReproTasklets.repro(
            DISCGRP_PARAMETER,
            CoreCodecs.DISCLOSURE_GROUP,
            jdbc,
            "INSERT INTO disclosure_group (dis_acct_group_id, dis_tran_type_cd, dis_tran_cat_cd,"
                + " dis_int_rate, filler) VALUES (?, ?, ?, ?, ?)",
            (DisclosureGroupRecord r) ->
                new Object[] {
                  r.disAcctGroupId(),
                  r.disTranTypeCd(),
                  r.disTranCatCd(),
                  r.disIntRate(),
                  r.filler()
                },
            sysout));
  }
}
