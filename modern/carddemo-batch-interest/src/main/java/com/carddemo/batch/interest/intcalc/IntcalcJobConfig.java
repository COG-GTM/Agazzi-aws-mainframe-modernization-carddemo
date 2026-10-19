package com.carddemo.batch.interest.intcalc;

import com.carddemo.batch.interest.support.B2JobListener;
import com.carddemo.batch.interest.support.B2StepListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** app/jcl/INTCALC.jcl: {@code //STEP15 EXEC PGM=CBACT04C,PARM='2022071800'}. */
@Configuration
public class IntcalcJobConfig {

  public static final String JOB = "INTCALC";

  @Bean
  Step intcalcStep15(
      JobRepository repository,
      PlatformTransactionManager transactionManager,
      IntcalcTasklet tasklet,
      B2StepListener listener) {
    return new StepBuilder("STEP15", repository)
        .tasklet(tasklet, transactionManager)
        .listener(listener)
        .build();
  }

  @Bean
  Job intcalcJob(JobRepository repository, Step intcalcStep15, B2JobListener listener) {
    return new JobBuilder(JOB, repository)
        .incrementer(new RunIdIncrementer())
        .listener(listener)
        .start(intcalcStep15)
        .build();
  }
}
