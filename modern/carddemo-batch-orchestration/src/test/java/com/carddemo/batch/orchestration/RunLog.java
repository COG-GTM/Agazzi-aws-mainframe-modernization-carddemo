package com.carddemo.batch.orchestration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** What the stub jobs of the other workstreams did, and how they should misbehave. */
public final class RunLog {

  public static final List<String> RUNS = Collections.synchronizedList(new ArrayList<>());
  public static final Set<String> FAIL_ONCE = ConcurrentHashMap.newKeySet();
  public static final Map<String, Runnable> ON_RUN = new ConcurrentHashMap<>();

  private RunLog() {}

  public static void reset() {
    RUNS.clear();
    FAIL_ONCE.clear();
    ON_RUN.clear();
  }

  public static List<String> runs() {
    synchronized (RUNS) {
      return List.copyOf(RUNS);
    }
  }
}
