package com.carddemo.batch.orchestration.scheduler;

import com.carddemo.contracts.port.BusinessClock;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Calendar of the Control-M folders (enabled with {@code
 * carddemo.orchestration.scheduler.enabled}).
 */
public class SchedulerTriggers {

  private final JobLauncher launcher;
  private final BusinessClock clock;
  private final Job daily;
  private final Job weekly;
  private final Job monthly;

  public SchedulerTriggers(
      JobLauncher launcher, BusinessClock clock, Job daily, Job weekly, Job monthly) {
    this.launcher = launcher;
    this.clock = clock;
    this.daily = daily;
    this.weekly = weekly;
    this.monthly = monthly;
  }

  @Scheduled(cron = "${carddemo.orchestration.scheduler.daily-cron:0 0 1 * * *}")
  public JobExecution runDaily() throws Exception {
    return order(daily);
  }

  @Scheduled(cron = "${carddemo.orchestration.scheduler.weekly-cron:0 0 2 * * SAT}")
  public JobExecution runWeekly() throws Exception {
    return order(weekly);
  }

  @Scheduled(cron = "${carddemo.orchestration.scheduler.monthly-cron:0 0 3 1 * *}")
  public JobExecution runMonthly() throws Exception {
    return order(monthly);
  }

  /** New day = new ODATE = new job instance. */
  JobExecution order(Job job) throws Exception {
    return launcher.run(
        job,
        new JobParametersBuilder()
            .addString("runDate", clock.today().toString())
            .toJobParameters());
  }
}
