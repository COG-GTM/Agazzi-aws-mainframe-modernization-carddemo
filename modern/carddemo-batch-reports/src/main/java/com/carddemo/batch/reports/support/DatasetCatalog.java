package com.carddemo.batch.reports.support;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.batch.core.JobParameters;

/**
 * Replaces the JCL {@code DD} statements: every dataset lives in the job parameter {@code
 * datasetDir} as {@code <DD>.dat}, unless the job parameter {@code dsn.<DD>} gives an explicit
 * path.
 */
public final class DatasetCatalog {

  public static final String DATASET_DIR = "datasetDir";
  public static final String DSN_PREFIX = "dsn.";

  private final Path dir;
  private final Map<String, String> overrides;

  public DatasetCatalog(Path dir, Map<String, String> overrides) {
    this.dir = dir;
    this.overrides = Map.copyOf(overrides);
  }

  public static DatasetCatalog from(JobParameters params) {
    String dir = params.getString(DATASET_DIR);
    if (dir == null || dir.isBlank()) {
      throw new IllegalArgumentException("Missing job parameter " + DATASET_DIR);
    }
    Map<String, String> overrides = new java.util.HashMap<>();
    params
        .getParameters()
        .forEach(
            (k, v) -> {
              if (k.startsWith(DSN_PREFIX)) {
                overrides.put(k.substring(DSN_PREFIX.length()), String.valueOf(v.getValue()));
              }
            });
    return new DatasetCatalog(Path.of(dir), overrides);
  }

  public Path path(String dd) {
    String o = overrides.get(dd);
    return o != null ? Path.of(o) : dir.resolve(dd + ".dat");
  }
}
