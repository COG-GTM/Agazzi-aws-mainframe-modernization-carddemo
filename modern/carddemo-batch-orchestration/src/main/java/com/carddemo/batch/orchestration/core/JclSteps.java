package com.carddemo.batch.orchestration.core;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.transaction.PlatformTransactionManager;

/** One JCL {@code //STEP EXEC} = one tasklet step carrying the JCL exit-status mapping. */
public class JclSteps {

  private final JobRepository repository;
  private final PlatformTransactionManager tx;

  public JclSteps(JobRepository repository, PlatformTransactionManager tx) {
    this.repository = repository;
    this.tx = tx;
  }

  public Step step(String name, Tasklet tasklet) {
    return new StepBuilder(name, repository)
        .tasklet(tasklet, tx)
        .listener(new JclExitStatusListener())
        .build();
  }

  public Step work(String name, StepWork work) {
    WorkStep step = new WorkStep(name, work);
    step.setJobRepository(repository);
    step.registerStepExecutionListener(new JclExitStatusListener());
    return step;
  }

  public JobRepository repository() {
    return repository;
  }
}
