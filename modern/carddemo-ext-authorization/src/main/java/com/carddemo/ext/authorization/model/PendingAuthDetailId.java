package com.carddemo.ext.authorization.model;

import java.io.Serializable;

/** Composite key of {@link PendingAuthDetail}: parent ACCNTID + PAUT9CTS. */
public record PendingAuthDetailId(Long acctId, Integer authDate9c, Long authTime9c)
    implements Serializable {}
