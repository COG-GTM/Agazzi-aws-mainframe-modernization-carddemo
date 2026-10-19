package com.carddemo.batch.orchestration.scheduler;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/** {@code app/scheduler/CardDemo.controlm}: folders, smart folders, DAYS and IN/OUT conditions. */
public final class ControlMDefinitions {

  public static final String DAILY = "DAILY-TransactionBackup";
  public static final String WEEKLY = "WEEKLY-TransactionTypesDBRefresh";
  public static final String WEEKLY_DISCLOSURE = "WEEKLY-DisclosureGroupsRefresh";
  public static final String MONTHLY = "MONTHLY-InterestCalculation";

  private static final Set<DayOfWeek> SATURDAY = Set.of(DayOfWeek.SATURDAY);

  private ControlMDefinitions() {}

  public static List<SchedulerFolder> all() {
    return List.of(daily(), weekly(), monthly());
  }

  /** DAYS=ALL: CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL. */
  public static SchedulerFolder daily() {
    return SchedulerFolder.chain(
        DAILY, DAILY, Set.of(), List.of("CLOSEFIL", "TRANBKP", "WAITSTEP", "OPENFIL"));
  }

  /** CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL. */
  public static SchedulerFolder monthly() {
    return SchedulerFolder.chain(
        MONTHLY,
        MONTHLY,
        Set.of(),
        List.of("CLOSEFIL", "INTCALC", "COMBTRAN", "WAITSTEP", "OPENFIL"));
  }

  /**
   * MNTTRDB2, then the two Saturday smart folders: DisclosureGroupsRefresh (CLOSEFIL → DISCGRP →
   * WAITSTEP → OPENFIL) and TransactionTypesDBRefresh (TRANEXTR), both waiting on MNTTRDB2. The
   * smart-folder level OUTCOND (-MNTTRDB2) only cleans the condition up: no job waits on it.
   */
  public static SchedulerFolder weekly() {
    String mnt = WEEKLY + "-MNTTRDB2";
    String dg = WEEKLY_DISCLOSURE + "-";
    String dgf = "DisclosureGroupsRefresh.";
    return new SchedulerFolder(
        WEEKLY,
        List.of(
            new SchedulerJob("MNTTRDB2", "MNTTRDB2", Set.of(), List.of(), List.of(mnt), List.of()),
            new SchedulerJob(
                dgf + "CLOSEFIL",
                "CLOSEFIL",
                SATURDAY,
                List.of(mnt),
                List.of(dg + "CLOSEFIL"),
                List.of()),
            new SchedulerJob(
                dgf + "DISCGRP",
                "DISCGRP",
                SATURDAY,
                List.of(dg + "CLOSEFIL"),
                List.of(dg + "DISCGRP"),
                List.of(dg + "CLOSEFIL")),
            new SchedulerJob(
                dgf + "WAITSTEP",
                "WAITSTEP",
                SATURDAY,
                List.of(dg + "DISCGRP"),
                List.of(dg + "WAITSTEP"),
                List.of(dg + "DISCGRP")),
            new SchedulerJob(
                dgf + "OPENFIL",
                "OPENFIL",
                SATURDAY,
                List.of(dg + "WAITSTEP"),
                List.of(),
                List.of(dg + "WAITSTEP")),
            new SchedulerJob(
                "TransactionTypesDBRefresh.TRANEXTR",
                "TRANEXTR",
                SATURDAY,
                List.of(mnt),
                List.of(),
                List.of())));
  }
}
