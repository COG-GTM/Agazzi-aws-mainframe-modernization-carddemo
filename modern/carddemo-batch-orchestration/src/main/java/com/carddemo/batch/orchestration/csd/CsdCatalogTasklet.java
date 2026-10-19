package com.carddemo.batch.orchestration.csd;

import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.batch.orchestration.core.JobParams;
import com.carddemo.contracts.port.BusinessClock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CBADMCDJ step STEP1 (DFHCSDUP): the CARDDEMO group is (re)loaded into {@code
 * cics_csd_definition}. A re-DEFINE of the same resource keeps the last one (counted as duplicate).
 */
public class CsdCatalogTasklet implements Tasklet {

  public static final String DEFAULT_SYSIN = "csd/CBADMCDJ.csdup";

  private final JdbcTemplate jdbc;
  private final BusinessClock clock;

  public CsdCatalogTasklet(JdbcTemplate jdbc, BusinessClock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunk) {
    String hlq = JobParams.string(chunk, "hlq", "AWS.M2.CARDDEMO");
    List<CsdDefinition> parsed;
    try {
      parsed = CsdParser.parse(sysin(JobParams.string(chunk, "sysin", null)), hlq);
    } catch (CsdParser.CsdSyntaxException e) {
      throw JclStepFailure.rc(12, "DFHCSDUP: " + e.getMessage());
    }
    Map<String, CsdDefinition> unique = new LinkedHashMap<>();
    parsed.forEach(d -> unique.put(d.type() + "/" + d.name() + "/" + d.group(), d));
    Timestamp now = Timestamp.valueOf(clock.now());
    unique.values().stream()
        .map(CsdDefinition::group)
        .distinct()
        .forEach(g -> jdbc.update("DELETE FROM cics_csd_definition WHERE csd_group = ?", g));
    for (CsdDefinition d : unique.values()) {
      jdbc.update(
          "INSERT INTO cics_csd_definition"
              + " (resource_type, resource_name, csd_group, attributes, defined_at)"
              + " VALUES (?, ?, ?, ?, ?)",
          d.type(),
          d.name(),
          d.group(),
          d.attributesText(),
          now);
    }
    var ctx = chunk.getStepContext().getStepExecution().getExecutionContext();
    ctx.putInt("defined", unique.size());
    ctx.putInt("duplicates", parsed.size() - unique.size());
    contribution.incrementWriteCount(unique.size());
    return RepeatStatus.FINISHED;
  }

  private static List<String> sysin(String path) {
    try {
      if (path == null) {
        return new String(
                new ClassPathResource(DEFAULT_SYSIN).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8)
            .lines()
            .toList();
      }
      Path p = Path.of(path);
      if (!Files.isRegularFile(p)) {
        throw JclStepFailure.rc(12, "DFHCSDUP: SYSIN not found " + p.getFileName());
      }
      return Files.readAllLines(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
