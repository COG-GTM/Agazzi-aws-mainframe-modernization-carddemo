package com.carddemo.ext.authorization.codec;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CCPAURLY reply as built by COPAUA0C {@code 6000-MAKE-DECISION}: padded fields separated by
 * commas, the approved amount edited with {@code PIC -zzzzzzzzz9.99}, and a trailing comma.
 */
public record AuthorizationReply(
    String cardNum,
    String transactionId,
    String authIdCode,
    String authRespCode,
    String authRespReason,
    BigDecimal approvedAmt) {

  public boolean approved() {
    return "00".equals(authRespCode);
  }

  public String toPayload() {
    return AuthorizationRequest.pad(cardNum, 16)
        + ','
        + AuthorizationRequest.pad(transactionId, 15)
        + ','
        + AuthorizationRequest.pad(authIdCode, 6)
        + ','
        + AuthorizationRequest.pad(authRespCode, 2)
        + ','
        + AuthorizationRequest.pad(authRespReason, 4)
        + ','
        + editAmount(approvedAmt)
        + ',';
  }

  /** {@code PIC -zzzzzzzzz9.99}. */
  public static String editAmount(BigDecimal v) {
    BigDecimal a = v.setScale(2, RoundingMode.DOWN);
    String sign = a.signum() < 0 ? "-" : " ";
    BigDecimal abs = a.abs();
    long intPart = abs.toBigInteger().mod(java.math.BigInteger.TEN.pow(10)).longValue();
    int cents = abs.remainder(BigDecimal.ONE).movePointRight(2).intValue();
    return sign + String.format("%10d", intPart) + '.' + String.format("%02d", cents);
  }
}
