package com.carddemo.batch.orchestration.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConditionPlannerTest {

  @Test
  void weeklyFolderFansOutAfterMnttrdb2() {
    var waves = ConditionPlanner.waves(ControlMDefinitions.weekly());
    assertThat(waves)
        .extracting(w -> w.stream().map(SchedulerJob::jobName).toList())
        .containsExactly(
            List.of("MNTTRDB2"),
            List.of("CLOSEFIL", "TRANEXTR"),
            List.of("DISCGRP"),
            List.of("WAITSTEP"),
            List.of("OPENFIL"));
  }

  @Test
  void ca7RefreshJoinsBothBranchesBeforeTheReads() {
    var waves = ConditionPlanner.waves(Ca7Definitions.refresh().toFolder());
    assertThat(waves.get(3))
        .extracting(SchedulerJob::id)
        .containsExactlyInAnyOrder("CLOSEFIL1", "CLOSEFIL2");
    assertThat(waves.get(6)).extracting(SchedulerJob::id).containsExactly("CLOSEFIL#2");
  }

  @Test
  void unreachableConditionIsRejected() {
    var folder =
        new SchedulerFolder(
            "BROKEN",
            List.of(new SchedulerJob("A", "A", Set.of(), List.of("NEVER"), List.of(), List.of())));
    assertThatThrownBy(() -> ConditionPlanner.waves(folder)).hasMessageContaining("[A]");
  }

  @Test
  void chainNumbersRepeatedJobsAndDropsTheLastOutcond() {
    var f = SchedulerFolder.chain("F", "P", Set.of(DayOfWeek.MONDAY), List.of("X", "X"));
    assertThat(f.jobs()).extracting(SchedulerJob::id).containsExactly("X", "X#2");
    assertThat(f.jobs().get(1).addConds()).isEmpty();
    assertThat(f.jobs().get(1).delConds()).containsExactly("P-X");
    assertThat(f.jobs().get(0).orderedOn(DayOfWeek.MONDAY)).isTrue();
    assertThat(f.jobs().get(0).orderedOn(DayOfWeek.TUESDAY)).isFalse();
  }
}
