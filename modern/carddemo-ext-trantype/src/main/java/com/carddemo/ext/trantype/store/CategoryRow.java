package com.carddemo.ext.trantype.store;

/** One TRANSACTION_CATEGORY row. */
public record CategoryRow(String type, int category, String description, String filler) {}
