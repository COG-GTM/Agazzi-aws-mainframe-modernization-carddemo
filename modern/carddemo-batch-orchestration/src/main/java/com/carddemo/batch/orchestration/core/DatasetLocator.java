package com.carddemo.batch.orchestration.core;

import java.nio.file.Path;
import java.util.regex.Pattern;

/** Catalog replacement for non-GDG sequential datasets: DSN → {@code <datasetsDir>/<DSN>}. */
public class DatasetLocator {

  private static final Pattern DSN =
      Pattern.compile("[A-Z#@$][A-Z0-9#@$-]{0,7}(\\.[A-Z#@$][A-Z0-9#@$-]{0,7})*");

  private final Path root;

  public DatasetLocator(Path root) {
    this.root = root;
  }

  public Path resolve(String dsn) {
    if (dsn == null || dsn.length() > 44 || !DSN.matcher(dsn).matches()) {
      throw JclStepFailure.abend("S813", "invalid DSN '" + dsn + "'", null);
    }
    return root.resolve(dsn);
  }

  public Path root() {
    return root;
  }
}
