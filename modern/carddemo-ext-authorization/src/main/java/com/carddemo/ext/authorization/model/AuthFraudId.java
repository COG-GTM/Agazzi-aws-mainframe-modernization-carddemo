package com.carddemo.ext.authorization.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Primary key of AUTHFRDS: (CARD_NUM, AUTH_TS). */
public record AuthFraudId(String cardNum, LocalDateTime authTs) implements Serializable {}
