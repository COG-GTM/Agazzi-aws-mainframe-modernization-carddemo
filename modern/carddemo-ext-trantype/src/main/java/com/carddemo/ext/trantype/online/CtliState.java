package com.carddemo.ext.trantype.online;

import java.io.Serial;
import java.io.Serializable;

/** WS-THIS-PROGCOMMAREA of COTRTLIC, kept in the HTTP session (ADR-002). */
public class CtliState implements Serializable {

  @Serial private static final long serialVersionUID = 1L;
  static final int ROWS = 7;

  boolean entered;
  String typeFilter;
  String descFilter;
  String firstKey = "";
  String lastKey = "";
  int screenNum;
  boolean nextPageExists;
  boolean lastPageShown;
  int rowSelected;
  boolean deleteRequested;
  boolean updateRequested;
  String[] codes = new String[ROWS];
  String[] descs = new String[ROWS];

  void resetPaging() {
    firstKey = "";
    lastKey = "";
    screenNum = 0;
    nextPageExists = false;
    lastPageShown = false;
    rowSelected = 0;
    deleteRequested = false;
    updateRequested = false;
  }

  /** INITIALIZE + SET CA-FIRST-PAGE / CA-LAST-PAGE-NOT-SHOWN. */
  void reset() {
    resetPaging();
    screenNum = 1;
    typeFilter = null;
    descFilter = null;
    codes = new String[ROWS];
    descs = new String[ROWS];
  }

  /** WS-CA-SCREEN-NUM is PIC 9(1): ADD 1 to 9 truncates to 0. */
  void nextScreen() {
    screenNum = (screenNum + 1) % 10;
  }

  public int screenNum() {
    return screenNum;
  }

  public String firstKey() {
    return firstKey;
  }

  public String lastKey() {
    return lastKey;
  }
}
