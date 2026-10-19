package com.carddemo.batch.orchestration.scheduler;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A Control-M folder / CA-7 trigger network, run as one Spring Batch job named {@link #name}. */
public record SchedulerFolder(String name, List<SchedulerJob> jobs) {

  public SchedulerFolder {
    jobs = List.copyOf(jobs);
  }

  /** Linear folder where each job consumes the previous one's condition (Control-M convention). */
  public static SchedulerFolder chain(
      String name, String condPrefix, Set<DayOfWeek> days, List<String> jclNames) {
    List<SchedulerJob> jobs = new ArrayList<>();
    String previous = null;
    for (String jcl : jclNames) {
      String id = uniqueId(jobs, jcl);
      String cond = condPrefix + "-" + id;
      jobs.add(
          new SchedulerJob(
              id,
              jcl,
              days,
              previous == null ? List.of() : List.of(previous),
              List.of(cond),
              previous == null ? List.of() : List.of(previous)));
      previous = cond;
    }
    List<SchedulerJob> fixed = new ArrayList<>(jobs);
    SchedulerJob last = fixed.remove(fixed.size() - 1);
    fixed.add(
        new SchedulerJob(
            last.id(), last.jobName(), last.days(), last.inConds(), List.of(), last.delConds()));
    return new SchedulerFolder(name, fixed);
  }

  private static String uniqueId(List<SchedulerJob> jobs, String jcl) {
    long n = jobs.stream().filter(j -> j.jobName().equals(jcl)).count();
    return n == 0 ? jcl : jcl + "#" + (n + 1);
  }
}
