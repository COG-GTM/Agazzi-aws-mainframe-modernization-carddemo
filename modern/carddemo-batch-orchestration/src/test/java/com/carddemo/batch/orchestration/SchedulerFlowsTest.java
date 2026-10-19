package com.carddemo.batch.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.orchestration.core.JclReturnCodes;
import com.carddemo.batch.orchestration.scheduler.Ca7Definitions;
import com.carddemo.batch.orchestration.scheduler.ControlMDefinitions;
import com.carddemo.batch.orchestration.scheduler.SchedulerFolder;
import com.carddemo.batch.orchestration.scheduler.SchedulerJobFactory;
import com.carddemo.contracts.port.MaintenanceModePort;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

class SchedulerFlowsTest extends BatchTestBase {

  @Autowired MaintenanceModePort maintenance;
  @Autowired SchedulerJobFactory factory;

  private JobExecution order(String folder, String runDate) throws Exception {
    return run(folder, params().addString("runDate", runDate).toJobParameters());
  }

  @Test
  void dailyTransactionBackupRunsInConditionOrder() throws Exception {
    JobExecution e = order(ControlMDefinitions.DAILY, "2022-07-18");
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(stepNames(e)).containsExactly("CLOSEFIL", "TRANBKP", "WAITSTEP", "OPENFIL");
    assertThat(RunLog.runs()).containsExactly("TRANBKP");
    assertThat(waits.waits).hasSize(1);
    assertThat(maintenance.closedFiles()).isEmpty();
  }

  @Test
  void monthlyInterestCalculation() throws Exception {
    JobExecution e = order(ControlMDefinitions.MONTHLY, "2022-07-01");
    assertThat(stepNames(e))
        .containsExactly("CLOSEFIL", "INTCALC", "COMBTRAN", "WAITSTEP", "OPENFIL");
    assertThat(RunLog.runs()).containsExactly("INTCALC", "COMBTRAN");
  }

  @Test
  void weeklySmartFoldersAreOrderedOnlyOnSaturday() throws Exception {
    JobExecution monday = order(ControlMDefinitions.WEEKLY, "2022-07-18");
    assertThat(monday.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(RunLog.runs()).containsExactly("MNTTRDB2");
    assertThat(
            step(monday, "TransactionTypesDBRefresh.TRANEXTR")
                .getExecutionContext()
                .getString("ordered"))
        .isEqualTo("N");

    RunLog.reset();
    JobExecution saturday = order(ControlMDefinitions.WEEKLY, "2022-07-23");
    assertThat(saturday.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    List<String> runs = RunLog.runs();
    assertThat(runs.get(0)).isEqualTo("MNTTRDB2");
    assertThat(runs).containsExactlyInAnyOrder("MNTTRDB2", "DISCGRP", "TRANEXTR");
    assertThat(stepNames(saturday)).hasSize(6);
    assertThat(maintenance.closedFiles()).isEmpty();
  }

  @Test
  void ca7NetworksFollowTheTriggers() throws Exception {
    assertThat(order(Ca7Definitions.POSTING, "2022-07-18").getStatus())
        .isEqualTo(BatchStatus.COMPLETED);
    assertThat(RunLog.runs()).containsExactly("CBPAUP0J", "POSTTRAN");

    RunLog.reset();
    waits.waits.clear();
    JobExecution refresh = order(Ca7Definitions.REFRESH, "2022-07-18");
    assertThat(refresh.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    List<String> runs = RunLog.runs();
    assertThat(runs.get(0)).isEqualTo("TRANTYPE");
    assertThat(runs.subList(1, 3)).containsExactlyInAnyOrder("TRANCATG", "TCATBALF");
    assertThat(runs.subList(3, 7)).containsExactly("READACCT", "READCARD", "READCUST", "READXREF");
    assertThat(waits.waits).hasSize(4);

    RunLog.reset();
    order(Ca7Definitions.STATEMENTS, "2022-07-18");
    assertThat(RunLog.runs()).containsExactly("CREASTMT", "TXT2PDF1", "PRTCATBL");
    assertThat(maintenance.closedFiles()).isEmpty();
  }

  @Test
  void fullBatchCycleRunsTheChainInSchedulerOrder() throws Exception {
    JobExecution e = run("fullBatchCycle");
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(RunLog.runs())
        .containsExactly("POSTTRAN", "INTCALC", "TRANBKP", "COMBTRAN", "TRANREPT", "CREASTMT");
    assertThat(stepNames(e))
        .containsExactly(
            "CLOSEFIL",
            "POSTTRAN",
            "INTCALC",
            "TRANBKP",
            "COMBTRAN",
            "TRANREPT",
            "CREASTMT",
            "OPENFIL");
    assertThat(JclReturnCodes.of(e)).isZero();
  }

  @Test
  void fullBatchCycleRestartsFromTheFailedJob() throws Exception {
    RunLog.FAIL_ONCE.add("INTCALC");
    var p = params().toJobParameters();
    JobExecution first = run("fullBatchCycle", p);
    assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(step(first, "INTCALC").getExitStatus().getExitCode()).isEqualTo("RC12");
    assertThat(JclReturnCodes.of(first)).isEqualTo(12);
    assertThat(stepNames(first)).containsExactly("CLOSEFIL", "POSTTRAN", "INTCALC");
    assertThat(maintenance.inMaintenance()).as("files stay closed after an abend").isTrue();

    JobExecution restart = run("fullBatchCycle", p);
    assertThat(restart.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(stepNames(restart))
        .containsExactly("INTCALC", "TRANBKP", "COMBTRAN", "TRANREPT", "CREASTMT", "OPENFIL");
    assertThat(RunLog.runs())
        .containsExactly(
            "POSTTRAN", "INTCALC", "INTCALC", "TRANBKP", "COMBTRAN", "TRANREPT", "CREASTMT");
    long intcalcInstances =
        explorer.getJobInstances("INTCALC", 0, 100).stream()
            .filter(i -> explorer.getJobExecutions(i).size() == 2)
            .count();
    assertThat(intcalcInstances)
        .as("the failed child instance is restarted, not re-created")
        .isEqualTo(1);
    assertThat(maintenance.inMaintenance()).isFalse();
  }

  @Test
  void nonIdentifyingParametersReachTheChildJobs() throws Exception {
    JobExecution e =
        run("fullBatchCycle", params().addString("files", "ACCTDAT", false).toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    long close = step(e, "CLOSEFIL").getExecutionContext().getLong("child.executionId");
    var child = explorer.getJobExecution(close);
    assertThat(child.getJobParameters().getParameter("files").isIdentifying()).isFalse();
    assertThat(step(child, "CLCIFIL").getExecutionContext().getString("files"))
        .isEqualTo("ACCTDAT");
  }

  @Test
  void unknownJobIsAJclErrorWithRc16() throws Exception {
    var job = factory.build(SchedulerFolder.chain("TESTNOJOB", "T", Set.of(), List.of("NOSUCHJB")));
    JobExecution e = launcher.run(job, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(step(e, "NOSUCHJB").getExitStatus().getExitCode()).isEqualTo(JclReturnCodes.NOJOB);
    assertThat(JclReturnCodes.of(e)).isEqualTo(16);
  }

  @Test
  void invalidRunDateEndsRc8() throws Exception {
    JobExecution e = order(ControlMDefinitions.DAILY, "18/07/2022");
    assertThat(step(e, "CLOSEFIL").getExitStatus().getExitCode()).isEqualTo("RC08");
  }

  @Test
  void repeatedJobInAChainGetsUniqueSlots() throws Exception {
    var job =
        factory.build(
            SchedulerFolder.chain("TESTREPEAT", "T", Set.of(), List.of("WAITSTEP", "WAITSTEP")));
    JobExecution e = launcher.run(job, params().toJobParameters());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(stepNames(e)).containsExactly("WAITSTEP", "WAITSTEP#2");
    assertThat(waits.waits).hasSize(2);
  }
}
