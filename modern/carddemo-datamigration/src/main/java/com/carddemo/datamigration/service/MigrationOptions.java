package com.carddemo.datamigration.service;

import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.SourceFormat;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.batch.core.JobParameters;

/** Parameters of the data migration jobs (CLI options = job parameters). */
public record MigrationOptions(
    SourceFormat source,
    Path input,
    Path output,
    List<Dataset> datasets,
    Mode mode,
    OnError onError,
    SourceFormat target,
    Path compare,
    boolean verify,
    Path listcat) {

  /** {@code upsert} keeps rows not in the extract; {@code replace} empties the tables first. */
  public enum Mode {
    UPSERT,
    REPLACE
  }

  /**
   * {@code abort}: any ERROR finding stops the load; {@code skip}: rejected records are skipped.
   */
  public enum OnError {
    ABORT,
    SKIP
  }

  public static MigrationOptions from(JobParameters p) {
    SourceFormat source = SourceFormat.parse(str(p, "source", "ascii"));
    String defaultInput = "app/data/" + (source == SourceFormat.ASCII ? "ASCII" : "EBCDIC");
    String input = str(p, "input", defaultInput);
    String compare = p.getString("compare");
    String listcat = p.getString("listcat");
    return new MigrationOptions(
        source,
        Path.of(input),
        Path.of(str(p, "output", "target/migrate-data")),
        Dataset.parseList(p.getString("datasets")),
        Mode.valueOf(str(p, "mode", "upsert").toUpperCase(Locale.ROOT)),
        OnError.valueOf(str(p, "onError", "abort").toUpperCase(Locale.ROOT)),
        SourceFormat.parse(str(p, "target", "ascii")),
        compare == null || compare.isBlank() ? null : Path.of(compare),
        Boolean.parseBoolean(str(p, "verify", "false")),
        listcat == null || listcat.isBlank() ? null : Path.of(listcat));
  }

  private static String str(JobParameters p, String k, String def) {
    String v = p.getString(k);
    return v == null || v.isBlank() ? def : v;
  }
}
