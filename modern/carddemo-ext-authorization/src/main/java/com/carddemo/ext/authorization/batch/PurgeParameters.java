package com.carddemo.ext.authorization.batch;

/**
 * CBPAUP0C {@code PRM-INFO} from SYSIN ({@code 99,XXXXX,XXXXX,X}): only {@code P-EXPIRY-DAYS}
 * drives the result; checkpoint frequencies and debug flag only affect SYSOUT.
 */
public record PurgeParameters(int expiryDays, boolean debug) {

  public static final int DEFAULT_EXPIRY_DAYS = 5;

  public static PurgeParameters parse(String sysin) {
    String p = sysin == null ? "" : sysin;
    p = p.length() < 17 ? p + " ".repeat(17 - p.length()) : p;
    String days = p.substring(0, 2);
    int expiry =
        days.chars().allMatch(c -> c >= '0' && c <= '9')
            ? Integer.parseInt(days)
            : DEFAULT_EXPIRY_DAYS;
    return new PurgeParameters(expiry, p.charAt(15) == 'Y');
  }
}
