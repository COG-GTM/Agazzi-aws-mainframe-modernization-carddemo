package com.carddemo.batch.posting.cbtrn02c;

/** Counters displayed at the end of CBTRN02C and the resulting RETURN-CODE. */
public record PostingResult(int processed, int rejected, int returnCode) {}
