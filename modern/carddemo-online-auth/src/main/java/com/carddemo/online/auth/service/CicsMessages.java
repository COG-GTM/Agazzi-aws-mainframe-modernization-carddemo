package com.carddemo.online.auth.service;

/** Literals of CSMSG01Y and of the O1 programs (texts identical to COBOL, padding removed). */
public final class CicsMessages {

  private CicsMessages() {}

  /** CSMSG01Y CCDA-MSG-THANK-YOU. */
  public static final String THANK_YOU = "Thank you for using CardDemo application...";

  /** CSMSG01Y CCDA-MSG-INVALID-KEY. */
  public static final String INVALID_KEY = "Invalid key pressed. Please see below...";

  public static final String ENTER_USER_ID = "Please enter User ID ...";
  public static final String ENTER_PASSWORD = "Please enter Password ...";
  public static final String WRONG_PASSWORD = "Wrong Password. Try again ...";
  public static final String USER_NOT_FOUND = "User not found. Try again ...";
  public static final String UNABLE_TO_VERIFY = "Unable to verify the User ...";
  public static final String INVALID_OPTION = "Please enter a valid option number...";

  /** COMEN01C admin-only option ('No access - Admin Only option... ', trailing space trimmed). */
  public static final String ADMIN_ONLY = "No access - Admin Only option...";

  /** COADM01C PGMIDERR-ERR-PARA / DUMMY option. */
  public static final String ADMIN_NOT_INSTALLED = "This option is not installed ...";
}
