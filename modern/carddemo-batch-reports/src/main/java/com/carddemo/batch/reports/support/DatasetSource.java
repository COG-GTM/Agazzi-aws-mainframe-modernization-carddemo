package com.carddemo.batch.reports.support;

import java.util.List;
import org.springframework.batch.core.JobParameters;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Where VSAM clusters are read from: job parameter {@code source=files} (default, {@code <DD>.dat}
 * in the catalog) or {@code source=db} (V1 tables unloaded by {@link JdbcDatasets}). Without {@code
 * source}, the {@code storage} parameter of B1/B2 and {@code fullBatchCycle} decides, so the
 * reports of a {@code storage=db} cycle read the tables POSTTRAN/INTCALC/COMBTRAN just updated.
 */
public final class DatasetSource {

  public static final String SOURCE = "source";
  public static final String STORAGE = "storage";

  private final DatasetCatalog catalog;
  private final JdbcDatasets jdbc;

  public DatasetSource(DatasetCatalog catalog, JdbcDatasets jdbc) {
    this.catalog = catalog;
    this.jdbc = jdbc;
  }

  public static DatasetSource from(JobParameters params, JdbcTemplate jdbc) {
    boolean db =
        "db".equalsIgnoreCase(params.getString(SOURCE, params.getString(STORAGE, "files")));
    if (db && jdbc == null) {
      throw new IllegalStateException("source=db requires a DataSource");
    }
    return new DatasetSource(DatasetCatalog.from(params), db ? new JdbcDatasets(jdbc) : null);
  }

  public DatasetCatalog catalog() {
    return catalog;
  }

  /** Reads a KSDS cluster in key order ({@code KEYS(keyLength keyOffset)}). */
  public Ksds ksds(String dd, int lrecl, int keyOffset, int keyLength) {
    List<String> recs =
        jdbc != null && JdbcDatasets.supports(dd)
            ? jdbc.unload(dd)
            : FixedRecords.read(required(dd), lrecl);
    return Ksds.load(dd, recs, keyOffset, keyLength);
  }

  /** Reads a sequential dataset of the catalog; a missing dataset is an allocation abend. */
  public List<String> sequential(String dd, int lrecl) {
    return FixedRecords.read(required(dd), lrecl);
  }

  private java.nio.file.Path required(String dd) {
    java.nio.file.Path path = catalog.path(dd);
    if (!java.nio.file.Files.exists(path)) {
      throw new JobAbendException(dd, "DATASET NOT FOUND: " + path);
    }
    return path;
  }

  public void write(String dd, List<String> records, int lrecl) {
    FixedRecords.write(catalog.path(dd), records, lrecl);
  }
}
