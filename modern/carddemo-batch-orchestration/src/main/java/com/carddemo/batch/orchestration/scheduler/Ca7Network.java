package com.carddemo.batch.orchestration.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A CA-7 trigger network ({@code LJOB ... TRIGGERED JOBS}): a job is submitted when all the jobs
 * that trigger it have completed. Mapped onto {@link SchedulerJob} conditions {@code <net>-<id>}.
 */
public record Ca7Network(String name, List<Node> nodes) {

  /** {@code ca7Name} as listed (CLOSEFIL1), {@code jcl} the member really run (CLOSEFIL). */
  public record Node(
      String id, String ca7Name, String jcl, String schid, List<String> triggeredBy) {}

  /** CA-7 edge {@code LJOB,JOB=from → JOB=to SCHID=schid}. */
  public record Edge(String from, String to, String schid) {}

  public List<Edge> edges() {
    Map<String, Node> byId =
        nodes.stream().collect(Collectors.toMap(Node::id, Function.identity()));
    List<Edge> edges = new ArrayList<>();
    for (Node n : nodes) {
      for (String p : n.triggeredBy()) {
        edges.add(new Edge(byId.get(p).ca7Name(), n.ca7Name(), n.schid()));
      }
    }
    return edges;
  }

  public SchedulerFolder toFolder() {
    Set<String> triggering =
        nodes.stream().flatMap(n -> n.triggeredBy().stream()).collect(Collectors.toSet());
    List<SchedulerJob> jobs = new ArrayList<>();
    for (Node n : nodes) {
      List<String> in = n.triggeredBy().stream().map(p -> name + "-" + p).toList();
      List<String> out = triggering.contains(n.id()) ? List.of(name + "-" + n.id()) : List.of();
      jobs.add(new SchedulerJob(n.id(), n.jcl(), Set.of(), in, out, List.of()));
    }
    return new SchedulerFolder(name, jobs);
  }
}
