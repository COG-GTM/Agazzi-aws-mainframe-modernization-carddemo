package com.carddemo.batch.orchestration.scheduler;

import com.carddemo.batch.orchestration.core.JclSteps;
import com.carddemo.batch.orchestration.launch.LaunchJobTasklet;
import com.carddemo.batch.orchestration.launch.RegisteredJobs;
import com.carddemo.contracts.port.BusinessClock;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.core.task.TaskExecutor;

/** Folder → Spring Batch {@code Flow}: one step per scheduler job, a split per parallel wave. */
public class SchedulerJobFactory {

  private final JclSteps steps;
  private final RegisteredJobs jobs;
  private final JobLauncher launcher;
  private final BusinessClock clock;
  private final TaskExecutor splitExecutor;

  public SchedulerJobFactory(
      JclSteps steps,
      RegisteredJobs jobs,
      JobLauncher launcher,
      BusinessClock clock,
      TaskExecutor splitExecutor) {
    this.steps = steps;
    this.jobs = jobs;
    this.launcher = launcher;
    this.clock = clock;
    this.splitExecutor = splitExecutor;
  }

  public Job build(SchedulerFolder folder) {
    List<List<SchedulerJob>> waves = ConditionPlanner.waves(folder);
    FlowBuilder<SimpleFlow> main = new FlowBuilder<>(folder.name() + ".flow");
    for (int i = 0; i < waves.size(); i++) {
      Flow wave = wave(folder.name() + ".wave" + (i + 1), waves.get(i));
      if (i == 0) {
        main.start(wave);
      } else {
        main.next(wave);
      }
    }
    return new JobBuilder(folder.name(), steps.repository())
        .incrementer(new RunIdIncrementer())
        .start(main.build())
        .end()
        .build();
  }

  private Flow wave(String name, List<SchedulerJob> wave) {
    if (wave.size() == 1) {
      return slot(wave.get(0));
    }
    return new FlowBuilder<SimpleFlow>(name)
        .split(splitExecutor)
        .add(wave.stream().map(this::slot).toArray(Flow[]::new))
        .build();
  }

  private Flow slot(SchedulerJob job) {
    LaunchJobTasklet launch = new LaunchJobTasklet(jobs, launcher, job.jobName());
    return new FlowBuilder<SimpleFlow>(job.id())
        .start(steps.work(job.id(), new ScheduledSlotTasklet(job, launch, clock)))
        .build();
  }
}
