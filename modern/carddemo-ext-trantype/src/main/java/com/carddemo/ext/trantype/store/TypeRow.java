package com.carddemo.ext.trantype.store;

/** One TRANSACTION_TYPE row (TR_TYPE, TR_DESCRIPTION); description kept padded as stored. */
public record TypeRow(String type, String description) {}
