package com.carddemo.batch.orchestration.csd;

import com.carddemo.batch.orchestration.core.DatasetLocator;
import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.batch.orchestration.core.JobParams;
import com.carddemo.contracts.port.BusinessClock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CBADMCDJ step STEP1 (DFHCSDUP): DELETE and DEFINE applied to {@code cics_csd_definition} in SYSIN
 * order; a DEFINE of an existing resource replaces it (version + 1), so reruns are idempotent.
 */
public class CsdCatalogTasklet implements Tasklet {

  public static final String DEFAULT_SYSIN = "csd/CBADMCDJ.csdup";

  private final JdbcTemplate jdbc;
  private final BusinessClock clock;
  private final DatasetLocator datasets;

  public CsdCatalogTasklet(JdbcTemplate jdbc, BusinessClock clock, DatasetLocator datasets) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.datasets = datasets;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunk) {
    String hlq = JobParams.string(chunk, "hlq", "AWS.M2.CARDDEMO");
    List<CsdParser.CsdCommand> commands;
    try {
      commands = CsdParser.commands(sysin(JobParams.string(chunk, "sysin", null)), hlq);
    } catch (CsdParser.CsdSyntaxException e) {
      throw JclStepFailure.rc(12, "DFHCSDUP: " + e.getMessage());
    }
    Timestamp now = Timestamp.valueOf(clock.now());
    int defined = 0;
    int redefined = 0;
    int deleted = 0;
    for (CsdParser.CsdCommand c : commands) {
      if (c instanceof CsdParser.Delete d) {
        deleted +=
            d.type() == null
                ? jdbc.update("DELETE FROM cics_csd_definition WHERE csd_group = ?", d.group())
                : jdbc.update(
                    "DELETE FROM cics_csd_definition"
                        + " WHERE resource_type = ? AND resource_name = ? AND csd_group = ?",
                    d.type(),
                    d.name(),
                    d.group());
      } else if (c instanceof CsdParser.Define(CsdDefinition d)) {
        int updated =
            jdbc.update(
                "UPDATE cics_csd_definition SET attributes = ?, defined_at = ?, version = version + 1"
                    + " WHERE resource_type = ? AND resource_name = ? AND csd_group = ?",
                d.attributesText(),
                now,
                d.type(),
                d.name(),
                d.group());
        if (updated == 0) {
          jdbc.update(
              "INSERT INTO cics_csd_definition"
                  + " (resource_type, resource_name, csd_group, attributes, defined_at)"
                  + " VALUES (?, ?, ?, ?, ?)",
              d.type(),
              d.name(),
              d.group(),
              d.attributesText(),
              now);
        } else {
          redefined++;
        }
        defined++;
      }
    }
    var ctx = chunk.getStepContext().getStepExecution().getExecutionContext();
    ctx.putInt("defined", defined);
    ctx.putInt("redefined", redefined);
    ctx.putInt("deleted", deleted);
    contribution.incrementWriteCount(defined);
    return RepeatStatus.FINISHED;
  }

  /**
   * {@code sysin}: a DSN of the dataset catalog (never an arbitrary path), else the bundled SYSIN.
   */
  private List<String> sysin(String dsn) {
    try {
      if (dsn == null) {
        return new String(
                new ClassPathResource(DEFAULT_SYSIN).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8)
            .lines()
            .toList();
      }
      Path p = datasets.resolve(dsn);
      if (!Files.isRegularFile(p)) {
        throw JclStepFailure.rc(12, "DFHCSDUP: SYSIN not found " + p.getFileName());
      }
      return Files.readAllLines(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
