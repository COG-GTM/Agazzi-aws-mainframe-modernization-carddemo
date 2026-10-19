package com.carddemo.batch.orchestration.launch;

import java.util.Optional;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.beans.factory.ListableBeanFactory;

/**
 * Jobs of other workstreams are resolved by JCL name at run time: first the {@link JobRegistry},
 * then any {@code Job} bean of the application context with that name. Never a Maven dependency.
 */
public class RegisteredJobs {

  private final JobRegistry registry;
  private final ListableBeanFactory beans;

  public RegisteredJobs(JobRegistry registry, ListableBeanFactory beans) {
    this.registry = registry;
    this.beans = beans;
  }

  public Optional<Job> find(String name) {
    try {
      return Optional.of(registry.getJob(name));
    } catch (NoSuchJobException e) {
      return beans.getBeansOfType(Job.class, false, false).values().stream()
          .filter(j -> j.getName().equals(name))
          .findFirst();
    }
  }
}
