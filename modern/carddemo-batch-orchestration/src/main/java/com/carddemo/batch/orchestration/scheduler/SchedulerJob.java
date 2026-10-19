package com.carddemo.batch.orchestration.scheduler;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/**
 * A Control-M job (or CA-7 trigger node): the JCL it runs, the calendar days it is ordered (empty =
 * every day) and its IN / OUT(+) / OUT(-) conditions.
 */
public record SchedulerJob(
    String id,
    String jobName,
    Set<DayOfWeek> days,
    List<String> inConds,
    List<String> addConds,
    List<String> delConds) {

  public SchedulerJob {
    days = Set.copyOf(days);
    inConds = List.copyOf(inConds);
    addConds = List.copyOf(addConds);
    delConds = List.copyOf(delConds);
  }

  public boolean orderedOn(DayOfWeek day) {
    return days.isEmpty() || days.contains(day);
  }
}
