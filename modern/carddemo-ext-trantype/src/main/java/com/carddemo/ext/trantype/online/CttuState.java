package com.carddemo.ext.trantype.online;

import java.io.Serial;
import java.io.Serializable;

/** TTUP work area of COTRTUPC (COMMAREA extension), kept in the HTTP session. */
public class CttuState implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** TTUP-CHANGE-ACTION values. */
  public enum Mode {
    NOT_FETCHED,
    INVALID_SEARCH_KEYS,
    DETAILS_NOT_FOUND,
    SHOW_DETAILS,
    CREATE_NEW_RECORD,
    CONFIRM_DELETE,
    START_DELETE,
    DELETE_DONE,
    DELETE_FAILED,
    CHANGES_NOT_OK,
    CHANGES_OK_NOT_CONFIRMED,
    CHANGES_OKAYED_LOCK_ERROR,
    CHANGES_OKAYED_BUT_FAILED,
    CHANGES_OKAYED_AND_DONE,
    CHANGES_BACKED_OUT
  }

  boolean entered;
  Mode mode = Mode.NOT_FETCHED;
  String oldType;
  String oldDesc;
  String newType;
  String newDesc;
  String fromTranId;
  String fromProgram;

  public Mode mode() {
    return mode;
  }

  void reset() {
    mode = Mode.NOT_FETCHED;
    oldType = null;
    oldDesc = null;
    newType = null;
    newDesc = null;
  }

  /** Records the caller for PF3 (CDEMO-FROM-TRANID / CDEMO-FROM-PROGRAM). */
  public void enteredFrom(String tranId, String program) {
    entered = false;
    fromTranId = tranId;
    fromProgram = program;
  }
}
