package com.carddemo.batch.orchestration.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carddemo.platform.time.FixedBusinessClock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

class SchedulerTriggersTest {

  @Test
  void eachTriggerOrdersItsFolderForTheBusinessDate() throws Exception {
    JobLauncher launcher = mock(JobLauncher.class);
    Job d = mock(Job.class);
    Job w = mock(Job.class);
    Job m = mock(Job.class);
    var triggers = new SchedulerTriggers(launcher, FixedBusinessClock.golden(), d, w, m);
    triggers.runDaily();
    triggers.runWeekly();
    triggers.runMonthly();
    ArgumentCaptor<JobParameters> p = ArgumentCaptor.forClass(JobParameters.class);
    verify(launcher).run(eq(d), p.capture());
    verify(launcher).run(eq(w), any());
    verify(launcher).run(eq(m), any());
    assertThat(p.getValue().getString("runDate")).isEqualTo("2022-07-18");
  }
}
