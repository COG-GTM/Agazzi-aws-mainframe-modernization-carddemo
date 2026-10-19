package com.carddemo.batch.orchestration.scheduler;

import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.batch.orchestration.core.JobParams;
import com.carddemo.batch.orchestration.core.StepWork;
import com.carddemo.contracts.port.BusinessClock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.batch.core.StepExecution;

/** Orders the job only on its calendar days ({@code runDate} = Control-M ODATE), then runs it. */
public class ScheduledSlotTasklet implements StepWork {

  private final SchedulerJob job;
  private final StepWork launch;
  private final BusinessClock clock;

  public ScheduledSlotTasklet(SchedulerJob job, StepWork launch, BusinessClock clock) {
    this.job = job;
    this.launch = launch;
    this.clock = clock;
  }

  @Override
  public void run(StepExecution step) throws Exception {
    LocalDate odate;
    try {
      odate = LocalDate.parse(JobParams.string(step, "runDate", clock.today().toString()));
    } catch (DateTimeParseException e) {
      throw JclStepFailure.rc(8, "invalid runDate: " + e.getParsedString());
    }
    if (!job.orderedOn(odate.getDayOfWeek())) {
      step.getExecutionContext().putString("ordered", "N");
      return;
    }
    launch.run(step);
  }
}
