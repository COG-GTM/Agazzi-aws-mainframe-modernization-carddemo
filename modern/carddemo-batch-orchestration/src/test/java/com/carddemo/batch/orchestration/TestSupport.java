package com.carddemo.batch.orchestration;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.WaitService;
import com.carddemo.platform.time.FixedBusinessClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Test doubles: golden business clock, recording WaitService, and stub jobs registered BY NAME in
 * the JobRegistry for the jobs owned by other workstreams (B1–B4, data migration).
 */
@Configuration(proxyBeanMethods = false)
public class TestSupport {

  public static final List<String> FOREIGN_JOBS =
      List.of(
          "POSTTRAN",
          "INTCALC",
          "COMBTRAN",
          "TRANREPT",
          "CREASTMT",
          "TRANBKP",
          "MNTTRDB2",
          "DISCGRP",
          "TRANEXTR",
          "CBPAUP0J",
          "TRANTYPE",
          "TRANCATG",
          "TCATBALF",
          "READACCT",
          "READCARD",
          "READCUST",
          "READXREF",
          "TXT2PDF1",
          "PRTCATBL");

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }

  @Bean
  RecordingWait recordingWait() {
    return new RecordingWait();
  }

  @Bean
  SmartInitializingSingleton stubJobs(
      JobRegistry registry, JobRepository repository, PlatformTransactionManager tx) {
    return () -> {
      for (String name : FOREIGN_JOBS) {
        Job job =
            new JobBuilder(name, repository)
                .start(
                    new StepBuilder("STEP", repository)
                        .tasklet(
                            (contribution, chunk) -> {
                              RunLog.RUNS.add(name);
                              if (RunLog.FAIL_ONCE.remove(name)) {
                                throw new IllegalStateException(name + " forced abend");
                              }
                              RunLog.ON_RUN.getOrDefault(name, () -> {}).run();
                              return RepeatStatus.FINISHED;
                            },
                            tx)
                        .build())
                .build();
        try {
          registry.register(new ReferenceJobFactory(job));
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }
    };
  }

  /** Records waits instead of sleeping. */
  public static class RecordingWait implements WaitService {
    public final List<Duration> waits = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void await(Duration duration) {
      waits.add(duration);
    }
  }
}
