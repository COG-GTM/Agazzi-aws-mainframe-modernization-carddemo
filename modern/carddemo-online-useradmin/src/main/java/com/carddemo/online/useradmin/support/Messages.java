package com.carddemo.online.useradmin.support;

/** Message literals shared by COUSR00C-COUSR03C (CSMSG01Y and inline literals). */
public final class Messages {

  /** CCDA-MSG-INVALID-KEY of CSMSG01Y (trailing filler removed). */
  public static final String INVALID_KEY = "Invalid key pressed. Please see below...";

  public static final String USER_ID_EMPTY = "User ID can NOT be empty...";
  public static final String FIRST_NAME_EMPTY = "First Name can NOT be empty...";
  public static final String LAST_NAME_EMPTY = "Last Name can NOT be empty...";
  public static final String PASSWORD_EMPTY = "Password can NOT be empty...";
  public static final String USER_TYPE_EMPTY = "User Type can NOT be empty...";
  public static final String USER_NOT_FOUND = "User ID NOT found...";
  public static final String UNABLE_TO_LOOKUP = "Unable to lookup User...";
  public static final String UNABLE_TO_UPDATE = "Unable to Update User...";
  public static final String UNABLE_TO_ADD = "Unable to Add User...";
  public static final String USER_ALREADY_EXISTS = "User ID already exist...";
  public static final String PRESS_PF5_UPDATE = "Press PF5 key to save your updates ...";
  public static final String PRESS_PF5_DELETE = "Press PF5 key to delete this user ...";
  public static final String PLEASE_MODIFY = "Please modify to update ...";
  public static final String INVALID_SELECTION = "Invalid selection. Valid values are U and D";
  public static final String ALREADY_TOP = "You are already at the top of the page...";
  public static final String ALREADY_BOTTOM = "You are already at the bottom of the page...";
  public static final String AT_TOP = "You are at the top of the page...";
  public static final String REACHED_BOTTOM = "You have reached the bottom of the page...";
  public static final String REACHED_TOP = "You have reached the top of the page...";

  private Messages() {}

  public static String added(String userId) {
    return "User " + Fields.delimitedBySpace(userId) + " has been added ...";
  }

  public static String updated(String userId) {
    return "User " + Fields.delimitedBySpace(userId) + " has been updated ...";
  }

  public static String deleted(String userId) {
    return "User " + Fields.delimitedBySpace(userId) + " has been deleted ...";
  }
}
