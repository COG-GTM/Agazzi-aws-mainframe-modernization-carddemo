package com.carddemo.batch.orchestration.scheduler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Control-M condition semantics turned into execution waves: a job starts when all its IN
 * conditions exist; when a wave ends its OUT(+) are added, then its OUT(-) deleted. Jobs of the
 * same wave are independent (Spring Batch split).
 */
public final class ConditionPlanner {

  private ConditionPlanner() {}

  public static List<List<SchedulerJob>> waves(SchedulerFolder folder) {
    List<SchedulerJob> pending = new ArrayList<>(folder.jobs());
    Set<String> conds = new LinkedHashSet<>();
    List<List<SchedulerJob>> waves = new ArrayList<>();
    while (!pending.isEmpty()) {
      List<SchedulerJob> ready =
          pending.stream().filter(j -> conds.containsAll(j.inConds())).toList();
      if (ready.isEmpty()) {
        throw new IllegalStateException(
            folder.name()
                + ": jobs waiting on conditions never produced: "
                + pending.stream().map(SchedulerJob::id).toList());
      }
      ready.forEach(j -> conds.addAll(j.addConds()));
      ready.forEach(j -> j.delConds().forEach(conds::remove));
      pending.removeAll(ready);
      waves.add(ready);
    }
    return waves;
  }
}
