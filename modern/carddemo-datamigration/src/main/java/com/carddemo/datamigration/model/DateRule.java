package com.carddemo.datamigration.model;

/** A PIC X date/timestamp field that must hold a valid calendar value. */
public record DateRule(String cobolName, Kind kind, boolean blankAllowed) {

  /** Accepted layouts. */
  public enum Kind {
    /** {@code YYYY-MM-DD}. */
    DATE,
    /** {@code YYYY-MM-DD HH:MM:SS.ffffff} (CICS/DB2 style, 26 bytes). */
    TIMESTAMP
  }

  public static DateRule date(String name) {
    return new DateRule(name, Kind.DATE, false);
  }

  public static DateRule timestamp(String name, boolean blankAllowed) {
    return new DateRule(name, Kind.TIMESTAMP, blankAllowed);
  }
}
