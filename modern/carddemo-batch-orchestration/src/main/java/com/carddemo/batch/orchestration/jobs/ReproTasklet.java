package com.carddemo.batch.orchestration.jobs;

import com.carddemo.batch.orchestration.core.DatasetLocator;
import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.batch.orchestration.core.JobParams;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * {@code IDCAMS REPRO IFILE(IN) OFILE(OUT)} for sequential datasets. Missing input = IDCAMS RC 12
 * (the job stops, as with the original JCL's implicit condition handling of the next job).
 */
public class ReproTasklet implements Tasklet {

  private final DatasetLocator datasets;
  private final String defaultIn;
  private final String defaultOut;

  public ReproTasklet(DatasetLocator datasets, String defaultIn, String defaultOut) {
    this.datasets = datasets;
    this.defaultIn = defaultIn;
    this.defaultOut = defaultOut;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunk) {
    Path in = datasets.resolve(JobParams.string(chunk, "in", defaultIn));
    Path out = datasets.resolve(JobParams.string(chunk, "out", defaultOut));
    if (!Files.isRegularFile(in)) {
      throw JclStepFailure.rc(12, "REPRO: input dataset not found " + in.getFileName());
    }
    try {
      Files.createDirectories(out.getParent());
      Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
      long records;
      try (var lines = Files.lines(in)) {
        records = lines.count();
      }
      contribution.incrementReadCount();
      contribution.incrementWriteCount((int) records);
      chunk.getStepContext().getStepExecution().getExecutionContext().putLong("records", records);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return RepeatStatus.FINISHED;
  }
}
