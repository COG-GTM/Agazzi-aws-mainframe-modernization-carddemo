package com.carddemo.datamigration.io;

/** A decoded legacy record and its 1-based position in the extract. */
public record SourceRecord(long ordinal, Record value) {}
