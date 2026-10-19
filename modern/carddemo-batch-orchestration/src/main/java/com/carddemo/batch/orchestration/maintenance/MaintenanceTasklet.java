package com.carddemo.batch.orchestration.maintenance;

import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.batch.orchestration.core.JobParams;
import com.carddemo.contracts.port.MaintenanceModePort;
import java.util.Set;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/** CLOSEFIL step CLCIFIL / OPENFIL step OPCIFIL: {@code CEMT SET FIL(x) CLO|OPE}. */
public class MaintenanceTasklet implements Tasklet {

  public enum Action {
    CLOSE,
    OPEN
  }

  private final MaintenanceModePort maintenance;
  private final Action action;

  public MaintenanceTasklet(MaintenanceModePort maintenance, Action action) {
    this.maintenance = maintenance;
    this.action = action;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunk) {
    Set<String> files;
    try {
      files = CicsFiles.parse(JobParams.string(chunk, "files", CicsFiles.DEFAULT_LIST));
    } catch (IllegalArgumentException e) {
      throw JclStepFailure.rc(8, e.getMessage());
    }
    if (action == Action.CLOSE) {
      maintenance.close(files);
    } else {
      maintenance.open(files);
    }
    chunk
        .getStepContext()
        .getStepExecution()
        .getExecutionContext()
        .putString("files", String.join(",", files));
    contribution.incrementWriteCount(files.size());
    return RepeatStatus.FINISHED;
  }
}
