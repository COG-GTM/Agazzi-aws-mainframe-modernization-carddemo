package com.carddemo.batch.orchestration.transfer;

import com.carddemo.batch.orchestration.core.DatasetLocator;
import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.batch.orchestration.core.JobParams;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/** FTPJCL step STEP1: {@code cd /ftpfolder} + {@code PUT 'AWS.M2.CARDEMO.FTP.TEST' welcome.txt}. */
public class TransferTasklet implements Tasklet {

  public static final String DEFAULT_DSN = "AWS.M2.CARDEMO.FTP.TEST";
  public static final String DEFAULT_REMOTE_NAME = "welcome.txt";

  private final FileTransferPort transfer;
  private final DatasetLocator datasets;
  private final String defaultRemoteDir;

  public TransferTasklet(
      FileTransferPort transfer, DatasetLocator datasets, String defaultRemoteDir) {
    this.transfer = transfer;
    this.datasets = datasets;
    this.defaultRemoteDir = defaultRemoteDir;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunk) {
    Path local = datasets.resolve(JobParams.string(chunk, "dataset", DEFAULT_DSN));
    if (!Files.isRegularFile(local)) {
      // z/OS FTP client: local file not found → transfer fails, step RC 8 (EXIT option style).
      throw JclStepFailure.rc(8, "FTP PUT: dataset not found " + local.getFileName());
    }
    String remoteDir = JobParams.string(chunk, "remoteDir", defaultRemoteDir);
    String remoteName = JobParams.string(chunk, "remoteName", DEFAULT_REMOTE_NAME);
    try {
      String where = transfer.put(local, remoteDir, remoteName);
      chunk.getStepContext().getStepExecution().getExecutionContext().putString("remote", where);
    } catch (IllegalArgumentException e) {
      throw JclStepFailure.rc(8, "FTP PUT: " + e.getMessage());
    }
    return RepeatStatus.FINISHED;
  }
}
