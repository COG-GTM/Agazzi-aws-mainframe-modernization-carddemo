package com.carddemo.datamigration.model;

import java.util.Locale;

/** Physical format of the legacy extract read by {@code vsamToRelational}. */
public enum SourceFormat {
  /** {@code app/data/ASCII/*.txt}: one record per line (LF or CRLF), possibly right-trimmed. */
  ASCII,
  /** {@code app/data/EBCDIC/*.PS}: CP037, fixed LRECL, no record separators. */
  EBCDIC,
  /** {@code AWS.M2.CARDDEMO.EXPORT.DATA.PS}: CVEXPORT multi-record file (CBEXPORT output). */
  EXPORT;

  public static SourceFormat parse(String s) {
    return valueOf(s.trim().toUpperCase(Locale.ROOT));
  }
}
