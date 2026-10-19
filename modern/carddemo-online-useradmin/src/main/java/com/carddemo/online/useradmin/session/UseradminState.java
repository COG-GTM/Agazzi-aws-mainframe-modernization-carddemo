package com.carddemo.online.useradmin.session;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * CDEMO-CU00-INFO / CDEMO-CU02-INFO / CDEMO-CU03-INFO: the program-specific tail that COUSR0xC
 * append to CARDDEMO-COMMAREA, plus the list rows currently on the COUSR0A screen (fields with
 * FSET, so they round-trip through RECEIVE MAP). Kept in the HTTP session next to the {@code
 * ConversationContext}.
 */
public final class UseradminState implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** One displayed row of COUSR0A (USRIDnn, FNAMEnn, LNAMEnn, UTYPEnn). */
  public record Row(String userId, String firstName, String lastName, String userType)
      implements Serializable {

    public static final Row BLANK = new Row("", "", "", "");
  }

  public static final int ROWS = 10;

  private String usridFirst = "";
  private String usridLast = "";
  private long pageNum;
  private boolean nextPage;
  private String selFlag = "";
  private String selected = "";
  private String pageNumDisplay = "";
  private Row[] rows = blankRows();

  static Row[] blankRows() {
    Row[] r = new Row[ROWS];
    Arrays.fill(r, Row.BLANK);
    return r;
  }

  public String usridFirst() {
    return usridFirst;
  }

  public void usridFirst(String v) {
    usridFirst = v;
  }

  public String usridLast() {
    return usridLast;
  }

  public void usridLast(String v) {
    usridLast = v;
  }

  public long pageNum() {
    return pageNum;
  }

  public void pageNum(long v) {
    pageNum = v;
  }

  public boolean nextPage() {
    return nextPage;
  }

  public void nextPage(boolean v) {
    nextPage = v;
  }

  public String selFlag() {
    return selFlag;
  }

  public void selFlag(String v) {
    selFlag = v;
  }

  public String selected() {
    return selected;
  }

  public void selected(String v) {
    selected = v;
  }

  public String pageNumDisplay() {
    return pageNumDisplay;
  }

  public void pageNumDisplay(String v) {
    pageNumDisplay = v;
  }

  public Row[] rows() {
    return rows.clone();
  }

  public void rows(Row[] v) {
    rows = v.clone();
  }

  /** Program left the user administration area: the CU tail of the commarea is lost. */
  public void reset() {
    usridFirst = "";
    usridLast = "";
    pageNum = 0;
    nextPage = false;
    selFlag = "";
    selected = "";
    pageNumDisplay = "";
    rows = blankRows();
  }
}
