package com.carddemo.batch.posting.job;

import java.nio.file.Path;
import org.springframework.batch.core.JobParameters;

/**
 * Job parameters of the B1 jobs (they replace the JCL DD statements / PARM).
 *
 * <ul>
 *   <li>{@code storage}: {@code db} (contracts-v1 tables, default) or {@code file}
 *   <li>{@code datasetDir}: directory with the {@code <DSNAME>.dat} clusters (file storage)
 *   <li>{@code dalytran}: DALYTRAN file (default: {@code daily_transaction} table / {@code
 *       datasetDir/DALYTRAN.dat})
 *   <li>{@code tranbkup}, {@code systran}: COMBTRAN SORTIN files (default: GDG generation 0)
 *   <li>{@code sysout}: file the step SYSOUT is appended to
 * </ul>
 */
public record PostingJobParameters(
    String storage, Path datasetDir, Path dalytran, Path tranbkup, Path systran, Path sysout) {

  public static final String STORAGE_DB = "db";
  public static final String STORAGE_FILE = "file";

  public static PostingJobParameters from(JobParameters p) {
    String storage = p.getString("storage", STORAGE_DB);
    return new PostingJobParameters(
        storage,
        path(p, "datasetDir"),
        path(p, "dalytran"),
        path(p, "tranbkup"),
        path(p, "systran"),
        path(p, "sysout"));
  }

  private static Path path(JobParameters p, String name) {
    String v = p.getString(name);
    return v == null || v.isBlank() ? null : Path.of(v);
  }

  public boolean fileStorage() {
    return STORAGE_FILE.equalsIgnoreCase(storage);
  }
}
