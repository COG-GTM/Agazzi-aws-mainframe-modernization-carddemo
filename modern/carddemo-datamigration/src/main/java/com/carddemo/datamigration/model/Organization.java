package com.carddemo.datamigration.model;

/** VSAM/PS organisation of a legacy dataset (drives key handling and unload order). */
public enum Organization {
  /** Key-sequenced: primary key = VSAM key, unload in key byte order. */
  KSDS,
  /** Physical sequential: surrogate {@code seq_no} = 1-based record ordinal. */
  PS,
  /** Entry-sequenced: surrogate {@code rba} = relative byte address ((n-1) * LRECL). */
  ESDS,
  /** Relative-record: surrogate {@code rrn} = 1-based relative record number. */
  RRDS
}
