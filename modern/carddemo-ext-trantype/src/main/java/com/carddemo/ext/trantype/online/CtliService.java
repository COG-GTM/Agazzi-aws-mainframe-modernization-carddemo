package com.carddemo.ext.trantype.online;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CotrtliScreen;
import com.carddemo.ext.trantype.TrantypeEnabled;
import com.carddemo.ext.trantype.store.Pic;
import com.carddemo.ext.trantype.store.TransactionTypeStore;
import com.carddemo.ext.trantype.store.TrantypeSqlException;
import com.carddemo.ext.trantype.store.TypeFilter;
import com.carddemo.ext.trantype.store.TypeRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** COTRTLIC (CTLI): transaction type list with filters, keyset paging, inline update/delete. */
@Service
@TrantypeEnabled
public class CtliService {

  static final String TRANID = "CTLI";
  static final String PGM = "COTRTLIC";
  static final String INFO_REC_ACTIONS = "Type U to update, D to delete any record";
  static final String INFO_DELETE = "Delete HIGHLIGHTED row ? Press F10 to confirm";
  static final String INFO_UPDATE = "Update HIGHLIGHTED row. Press F10 to save";
  static final String INFO_DELETE_DONE = "HIGHLIGHTED row deleted.Hit Enter to continue";
  static final String INFO_UPDATE_DONE = "HIGHLIGHTED row was updated";
  static final String MSG_NO_RECORDS = "No records found for this search condition.";
  static final String MSG_NO_MORE_RECORDS = "No more pages for these search conditions";
  static final String MSG_ONLY_ONE = "Please select only 1 action";
  static final String MSG_INVALID_ACTION = "Action code selected is invalid";
  static final String MSG_NO_CHANGES = "No change detected with respect to database values.";
  static final String MSG_TYPE_FILTER = "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER";
  static final String MSG_NO_FILTER_MATCH = "No Records found for these filter conditions";
  static final String MSG_NO_PREVIOUS = "No previous pages to display";
  static final String MSG_NO_MORE_PAGES = "No more pages to display";

  private final TransactionTypeStore store;
  private final BusinessClock clock;

  public CtliService(TransactionTypeStore store, BusinessClock clock) {
    this.store = store;
    this.clock = clock;
  }

  /** First send of CTRTLIA (EIBCALEN = 0 or XCTL from another program). */
  public ScreenResponse<CotrtliScreen.Output> firstSend(CtliState s, String user) {
    s.reset();
    s.entered = true;
    Task t = new Task(s, Action.ENTER, CtliInput.EMPTY);
    t.filter = TypeFilter.NONE;
    t.readForward(s.firstKey);
    return t.send(user);
  }

  public ScreenResponse<CotrtliScreen.Output> receive(
      Action aid, CtliInput input, CtliState s, String user) {
    if (!s.entered) {
      return firstSend(s, user);
    }
    Task t = new Task(s, aid, input == null ? CtliInput.EMPTY : input);
    t.editInputs();
    Action a = aid;
    boolean pendingConfirm = s.deleteRequested || s.updateRequested;
    boolean valid =
        a == Action.ENTER
            || a == Action.PF2
            || a == Action.PF3
            || a == Action.PF7
            || a == Action.PF8
            || (a == Action.PF10 && pendingConfirm);
    if (!valid) {
      a = Action.ENTER;
    }
    if (a == Action.PF3) {
      s.reset();
      s.entered = false;
      return exit(user, ScreenId.COADM01, ScreenSupport.ADMIN_TRANID, ScreenSupport.ADMIN_PGM);
    }
    if (a == Action.PF2) {
      s.entered = false;
      return exit(user, ScreenId.COTRTUP, CttuService.TRANID, CttuService.PGM);
    }
    if (a != Action.PF8) {
      s.lastPageShown = false;
    }
    if (a == Action.PF10
        && !(pendingConfirm && !t.typeChanged && !t.descChanged && !t.selectionChanged)) {
      a = Action.ENTER;
    }
    t.aid = a;
    if (t.inputError) {
      if (!t.typeNotOk && !t.descNotOk) {
        t.readForward(s.firstKey);
      }
      return t.send(user);
    }
    boolean firstPage = s.screenNum == 1;
    if (a == Action.PF7 && firstPage) {
      t.readForward(s.firstKey);
    } else if (a == Action.PF8 && s.nextPageExists) {
      s.nextScreen();
      t.readForward(s.lastKey);
      t.clearSelections();
    } else if (a == Action.PF7) {
      s.screenNum = Math.abs(s.screenNum - 1);
      t.readBackward(s.firstKey);
      t.clearSelections();
    } else if (a == Action.PF10 && t.deletes > 0) {
      t.delete();
      ScreenResponse<CotrtliScreen.Output> r = t.send(user);
      if (t.deleted) {
        s.reset();
      }
      return r;
    } else if (a == Action.PF10 && t.updates > 0) {
      t.update();
      t.readForward(s.firstKey);
    } else {
      t.readForward(s.firstKey);
    }
    return t.send(user);
  }

  private ScreenResponse<CotrtliScreen.Output> exit(
      String user, ScreenId next, String toTran, String toPgm) {
    return ScreenResponse.<CotrtliScreen.Output>of(
            ScreenId.COTRTLI, null, ScreenSupport.transfer(user, TRANID, PGM, toTran, toPgm))
        .withNextScreen(next);
  }

  /** Working storage of one pseudo-conversational task. */
  private final class Task {
    final CtliState s;
    final CtliInput in;
    Action aid;
    TypeFilter filter;
    String err = "";
    String info = "";
    boolean inputError;
    boolean typeValid;
    boolean typeNotOk;
    boolean typeChanged;
    boolean descValid;
    boolean descNotOk;
    boolean descChanged;
    boolean selectionChanged;
    boolean badActions;
    boolean changesOccurred;
    boolean rowDescBlank;
    boolean rowDescNotOk;
    boolean deleted;
    boolean updateCompleted;
    boolean noRecordsFound;
    int deletes;
    int updates;
    int actions;
    int validActions;
    int selected;
    final String[] sel = new String[CtliState.ROWS];
    final String[] descIn = new String[CtliState.ROWS];
    final boolean[] rowError = new boolean[CtliState.ROWS];

    Task(CtliState s, Action aid, CtliInput in) {
      this.s = s;
      this.aid = aid;
      this.in = in;
      String[] sels = in.selections();
      String[] descs = in.descriptions();
      for (int i = 0; i < CtliState.ROWS; i++) {
        sel[i] = Pic.isBlank(sels[i]) ? " " : sels[i].substring(0, 1);
        String d = descs[i];
        descIn[i] = d == null || "*".equals(d.strip()) || Pic.isBlank(d) ? null : d.strip();
      }
    }

    private void setMsg(String m) {
      err = m;
    }

    private void setMsgIfOff(String m) {
      if (err.isEmpty()) {
        err = m;
      }
    }

    /** 1200-EDIT-INPUTS: 1210 array, 1230 description, 1220 type code, 1290 cross edits. */
    void editInputs() {
      editArray();
      editDescFilter();
      editTypeFilter();
      filter =
          new TypeFilter(typeValid ? typeCode() : null, descValid ? in.trdesc().strip() : null);
      if (typeValid || descValid) {
        long n;
        try {
          n = store.count(filter);
        } catch (TrantypeSqlException e) {
          inputError = true;
          setMsgIfOff(
              ScreenSupport.db2Message(
                  "Error reading TRANSACTION_TYPE table ", e.sqlcode(), e.getMessage()));
          return;
        }
        if (n == 0) {
          inputError = true;
          typeNotOk = typeValid;
          descNotOk = descValid;
          setMsg(MSG_NO_FILTER_MATCH);
        }
      }
    }

    private String typeCode() {
      return Pic.pad(in.trtype(), 2);
    }

    private void editArray() {
      int blanks = 0;
      for (String x : sel) {
        if (" ".equals(x)) {
          blanks++;
        } else if ("D".equals(x)) {
          deletes++;
        } else if ("U".equals(x)) {
          updates++;
        }
      }
      actions = CtliState.ROWS - blanks;
      validActions = deletes + updates;
      selected = 0;
      for (int i = CtliState.ROWS - 1; i >= 0; i--) {
        if ("D".equals(sel[i]) || "U".equals(sel[i])) {
          selected = i + 1;
          if (actions > 1) {
            rowError[i] = true;
            badActions = true;
          }
          if ("U".equals(sel[i])) {
            editRowDescription(i);
          }
        } else if (!" ".equals(sel[i])) {
          inputError = true;
          rowError[i] = true;
          badActions = true;
          setMsg(MSG_INVALID_ACTION);
        }
      }
      if (selected == s.rowSelected) {
        selectionChanged = false;
      } else {
        selectionChanged = true;
        s.rowSelected = selected;
        if (actions > 1) {
          inputError = true;
          setMsg(MSG_ONLY_ONE);
        }
      }
    }

    /** 1211-EDIT-ARRAY-DESC. */
    private void editRowDescription(int i) {
      String now = descIn[i] == null ? "" : descIn[i].strip().toUpperCase(Locale.ROOT);
      String old = s.descs[i] == null ? "" : s.descs[i].strip().toUpperCase(Locale.ROOT);
      if (now.equals(old)) {
        setMsg(MSG_NO_CHANGES);
        return;
      }
      changesOccurred = true;
      if (descIn[i] == null) {
        inputError = true;
        rowDescBlank = true;
        setMsgIfOff("Transaction Desc must be supplied.");
      } else if (!ScreenSupport.isAlphanumeric(descIn[i])) {
        inputError = true;
        rowDescNotOk = true;
        setMsgIfOff("Transaction Desc can have numbers or alphabets only.");
      }
    }

    /** 1220-EDIT-TYPECD. */
    private void editTypeFilter() {
      String raw = in.trtype();
      if (Pic.isBlank(raw) || Pic.rtrim(raw).chars().allMatch(c -> c == '0')) {
        return;
      }
      String code = typeCode();
      if (!ScreenSupport.isNumeric2(code)) {
        inputError = true;
        typeNotOk = true;
        setMsg(MSG_TYPE_FILTER);
        return;
      }
      typeValid = true;
      if (!code.equals(s.typeFilter)) {
        s.resetPaging();
        s.typeFilter = code;
        typeChanged = true;
      }
    }

    /** 1230-EDIT-DESC. */
    private void editDescFilter() {
      String raw = in.trdesc();
      if (Pic.isBlank(raw)) {
        return;
      }
      descValid = true;
      String v = Pic.rtrim(raw);
      if (!v.equals(s.descFilter)) {
        s.resetPaging();
        s.descFilter = v;
        descChanged = true;
      }
    }

    void clearSelections() {
      for (int i = 0; i < CtliState.ROWS; i++) {
        sel[i] = " ";
      }
    }

    /** 8000-READ-FORWARD: one page plus one look-ahead row. */
    void readForward(String start) {
      List<TypeRow> rows;
      try {
        rows = store.forward(filter, start, CtliState.ROWS + 1);
      } catch (TrantypeSqlException e) {
        setMsgIfOff(
            ScreenSupport.db2Message("C-TR-TYPE-FORWARD fetch", e.sqlcode(), e.getMessage()));
        return;
      }
      s.codes = new String[CtliState.ROWS];
      s.descs = new String[CtliState.ROWS];
      int n = Math.min(CtliState.ROWS, rows.size());
      for (int i = 0; i < n; i++) {
        s.codes[i] = rows.get(i).type();
        s.descs[i] = rows.get(i).description();
      }
      if (n > 0) {
        s.firstKey = rows.get(0).type();
        if (s.screenNum == 0) {
          s.screenNum = 1;
        }
      }
      if (rows.size() > CtliState.ROWS) {
        s.nextPageExists = true;
        s.lastKey = rows.get(CtliState.ROWS).type();
        return;
      }
      s.nextPageExists = false;
      if (n > 0) {
        s.lastKey = rows.get(n - 1).type();
      }
      if (aid == Action.PF8) {
        setMsgIfOff(MSG_NO_MORE_RECORDS);
      }
      if (s.screenNum == 1 && n == 0) {
        noRecordsFound = true;
        setMsg(MSG_NO_RECORDS);
      }
    }

    /** 8100-READ-BACKWARDS: rows before the first key, filled bottom-up. */
    void readBackward(String start) {
      List<TypeRow> rows;
      try {
        rows = store.backward(filter, start, CtliState.ROWS);
      } catch (TrantypeSqlException e) {
        setMsgIfOff(
            ScreenSupport.db2Message(
                "Error on fetch Cursor C-TR-TYPE-BACKWARD", e.sqlcode(), e.getMessage()));
        return;
      }
      s.lastKey = s.firstKey;
      s.nextPageExists = true;
      s.codes = new String[CtliState.ROWS];
      s.descs = new String[CtliState.ROWS];
      int n = rows.size();
      for (int i = 0; i < n; i++) {
        int row = n - 1 - i;
        s.codes[row] = rows.get(i).type();
        s.descs[row] = rows.get(i).description();
      }
      if (n > 0) {
        s.firstKey = rows.get(n - 1).type();
      }
    }

    /** 9200-UPDATE-RECORD. */
    void update() {
      int i = selected - 1;
      try {
        store.updateDescription(s.codes[i], descIn[i]);
        updateCompleted = true;
      } catch (TrantypeSqlException e) {
        String action =
            switch (e.sqlcode()) {
              case TrantypeSqlException.NOT_FOUND -> "Record not found. Deleted by others ? ";
              case TrantypeSqlException.DEADLOCK -> "Deadlock. Someone else updating ?";
              default -> "Update failed with";
            };
        if (e.sqlcode() == TrantypeSqlException.DEADLOCK) {
          inputError = true;
        }
        setMsgIfOff(ScreenSupport.db2Message(action, e.sqlcode(), e.getMessage()));
      }
    }

    /** 9300-DELETE-RECORD. */
    void delete() {
      try {
        store.delete(s.codes[selected - 1]);
        deleted = true;
      } catch (TrantypeSqlException e) {
        String action =
            e.sqlcode() == TrantypeSqlException.CHILD_ROWS_EXIST
                ? "Please delete associated child records first:"
                : "Delete failed with message:";
        setMsgIfOff(ScreenSupport.db2Message(action, e.sqlcode(), e.getMessage()));
      }
    }

    /** 2000-SEND-MAP. */
    ScreenResponse<CotrtliScreen.Output> send(String user) {
      boolean onlyOne = validActions == 1 && !badActions;
      String[] outSel = new String[CtliState.ROWS];
      String[] outType = new String[CtliState.ROWS];
      String[] outDesc = new String[CtliState.ROWS];
      List<FieldError> fieldErrors = new ArrayList<>();
      s.deleteRequested = false;
      s.updateRequested = false;
      for (int i = 0; i < CtliState.ROWS; i++) {
        if (s.codes[i] == null) {
          outSel[i] = "";
          outType[i] = "";
          outDesc[i] = "";
          continue;
        }
        String selection = sel[i];
        String desc = Pic.rtrim(s.descs[i]);
        if ("D".equals(sel[i]) && onlyOne) {
          if (deleted) {
            selection = " ";
          } else {
            s.deleteRequested = true;
          }
        }
        if ("U".equals(sel[i]) && onlyOne) {
          if (updateCompleted) {
            selection = " ";
          } else {
            s.updateRequested = true;
            if (changesOccurred) {
              desc = rowDescBlank ? "*" : descIn[i];
            }
          }
        }
        outSel[i] = selection.strip();
        outType[i] = s.codes[i];
        outDesc[i] = desc;
        if (rowError[i]) {
          fieldErrors.add(new FieldError("TRTSEL" + (i + 1), err.strip()));
        }
        if ("U".equals(sel[i]) && (rowDescBlank || rowDescNotOk)) {
          fieldErrors.add(new FieldError("TRTYPD" + (i + 1), err.strip()));
        }
      }
      if (typeNotOk) {
        fieldErrors.add(0, new FieldError("TRTYPE", err.strip()));
      }
      if (descNotOk) {
        fieldErrors.add(typeNotOk ? 1 : 0, new FieldError("TRDESC", err.strip()));
      }
      setupMessage();
      String infoOut = noRecordsFound ? "" : info;
      CotrtliScreen.Output o =
          new CotrtliScreen.Output(
              TRANID,
              ScreenSupport.TITLE01,
              ScreenSupport.date(clock),
              PGM,
              ScreenSupport.TITLE02,
              ScreenSupport.time(clock),
              String.valueOf(s.screenNum),
              Pic.rtrim(in.trtype()),
              Pic.rtrim(in.trdesc()),
              outSel[0],
              outType[0],
              outDesc[0],
              outSel[1],
              outType[1],
              outDesc[1],
              outSel[2],
              outType[2],
              outDesc[2],
              outSel[3],
              outType[3],
              outDesc[3],
              outSel[4],
              outType[4],
              outDesc[4],
              outSel[5],
              outType[5],
              outDesc[5],
              outSel[6],
              outType[6],
              outDesc[6],
              null,
              null,
              null,
              infoOut,
              err.strip(),
              null,
              null,
              null,
              null,
              null);
      ScreenResponse<CotrtliScreen.Output> r =
          ScreenResponse.of(ScreenId.COTRTLI, o, ScreenSupport.context(user, TRANID, PGM));
      if (!infoOut.isEmpty()) {
        r = r.withMessage(ScreenMessage.info(infoOut));
      }
      if (!err.isBlank()) {
        r = r.withMessage(ScreenMessage.error(err.strip()));
      }
      for (FieldError fe : fieldErrors) {
        r = r.withFieldError(fe);
      }
      return r;
    }

    /** 2500-SETUP-MESSAGE. */
    private void setupMessage() {
      boolean filtersUnchanged = !typeChanged && !descChanged;
      if (deleted) {
        info = INFO_DELETE_DONE;
      } else if (updateCompleted) {
        info = INFO_UPDATE_DONE;
      } else if (typeNotOk || descNotOk) {
        info = "";
      } else if (aid == Action.ENTER && deletes > 0 && actions == 1 && validActions == 1) {
        info = filtersUnchanged ? INFO_DELETE : "";
      } else if (aid == Action.ENTER && updates > 0 && actions == 1 && validActions == 1) {
        info = filtersUnchanged ? INFO_UPDATE : "";
      } else if (aid == Action.PF7 && s.screenNum == 1) {
        setMsg(MSG_NO_PREVIOUS);
      } else if (aid == Action.PF8 && !s.nextPageExists && s.lastPageShown) {
        setMsg(MSG_NO_MORE_PAGES);
      } else if (aid == Action.PF8 && !s.nextPageExists) {
        info = INFO_REC_ACTIONS;
        s.lastPageShown = true;
      } else {
        info = INFO_REC_ACTIONS;
      }
    }
  }
}
