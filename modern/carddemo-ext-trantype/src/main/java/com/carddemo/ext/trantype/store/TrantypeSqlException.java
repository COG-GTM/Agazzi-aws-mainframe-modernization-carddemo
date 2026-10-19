package com.carddemo.ext.trantype.store;

/** Data access failure carrying the DB2 SQLCODE the legacy programs would have seen. */
public class TrantypeSqlException extends RuntimeException {

  public static final int NOT_FOUND = 100;
  public static final int DUPLICATE_KEY = -803;
  public static final int CHILD_ROWS_EXIST = -532;
  public static final int DEADLOCK = -911;
  public static final int GENERIC = -999;
  public static final String NOT_FOUND_TEXT = "ROW NOT FOUND";
  public static final String CHILD_ROWS_TEXT = "TRANSACTION_CATEGORY ROWS DEPEND ON THIS TYPE";
  public static final String DUPLICATE_KEY_TEXT = "DUPLICATE KEY";

  private final int sqlcode;

  public TrantypeSqlException(int sqlcode, String message) {
    super(message);
    this.sqlcode = sqlcode;
  }

  public int sqlcode() {
    return sqlcode;
  }
}
