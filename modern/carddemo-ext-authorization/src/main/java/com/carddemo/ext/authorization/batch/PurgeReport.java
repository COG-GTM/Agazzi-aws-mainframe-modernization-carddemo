package com.carddemo.ext.authorization.batch;

/** CBPAUP0C end-of-job counters. */
public record PurgeReport(
    int currentYyddd, long summaryRead, long summaryDeleted, long detailRead, long detailDeleted)
    implements java.io.Serializable {}
