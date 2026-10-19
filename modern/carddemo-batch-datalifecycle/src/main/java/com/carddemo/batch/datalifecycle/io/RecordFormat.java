package com.carddemo.batch.datalifecycle.io;

import java.nio.file.Path;
import java.util.Locale;

/** Physical layout of a dataset on the filesystem. */
public enum RecordFormat {
  /** Text lines (ASCII sample data); short lines are space-padded like LINE SEQUENTIAL. */
  LINES,
  /** Fixed records each followed by LF (golden {@code .dat}); safe for binary content. */
  FIXED_LF,
  /** Raw fixed records without separators (golden {@code .bin}). */
  FIXED,
  /** RECFM=VB: 4-byte RDW (big-endian length, two zero bytes) before each record. */
  VARIABLE;

  /** {@code .txt} lines, {@code .dat} fixed+LF, {@code .vb} variable, anything else raw. */
  public static RecordFormat of(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".txt")) {
      return LINES;
    }
    if (name.endsWith(".dat")) {
      return FIXED_LF;
    }
    if (name.endsWith(".vb")) {
      return VARIABLE;
    }
    return FIXED;
  }
}
