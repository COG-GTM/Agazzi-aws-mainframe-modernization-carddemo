package com.carddemo.ext.trantype.online;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CotrtupScreen;
import com.carddemo.ext.trantype.TrantypeEnabled;
import com.carddemo.ext.trantype.online.CttuState.Mode;
import com.carddemo.ext.trantype.store.Pic;
import com.carddemo.ext.trantype.store.TransactionTypeStore;
import com.carddemo.ext.trantype.store.TrantypeSqlException;
import com.carddemo.ext.trantype.store.TypeRow;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** COTRTUPC (CTTU): add / update / delete one transaction type. */
@Service
@TrantypeEnabled
public class CttuService {

  static final String TRANID = "CTTU";
  static final String PGM = "COTRTUPC";
  static final String PROMPT_SEARCH = "Enter transaction type to be maintained";
  static final String PROMPT_CREATE = "Press F05 to add. F12 to cancel";
  static final String PROMPT_DELETE = "Delete this record ? Press F4 to confirm";
  static final String DELETE_SUCCESS = "Delete successful.";
  static final String PROMPT_CHANGES = "Update transaction type details shown.";
  static final String PROMPT_NEWDATA = "Enter new transaction type details.";
  static final String PROMPT_CONFIRM = "Changes validated.Press F5 to save";
  static final String UPDATE_SUCCESS = "Changes committed to database";
  static final String INFORM_FAILURE = "Changes unsuccessful";
  static final String MSG_INVALID_KEY = "Invalid key pressed";
  static final String MSG_NOT_FOUND = "No record found for this key in database";
  static final String MSG_NO_CHANGES = "No change detected with respect to values fetched.";
  static final String MSG_LOCK = "Could not lock record for update";
  static final String MSG_UPDATE_CANCELLED = "Update was cancelled";
  static final String MSG_DELETE_CANCELLED = "Delete was cancelled";

  private static final EnumSet<Mode> RESET_ON_NEXT_KEY =
      EnumSet.of(
          Mode.CHANGES_OKAYED_AND_DONE,
          Mode.CHANGES_OKAYED_LOCK_ERROR,
          Mode.CHANGES_OKAYED_BUT_FAILED,
          Mode.DELETE_DONE,
          Mode.DELETE_FAILED);

  private final TransactionTypeStore store;
  private final BusinessClock clock;

  public CttuService(TransactionTypeStore store, BusinessClock clock) {
    this.store = store;
    this.clock = clock;
  }

  public ScreenResponse<CotrtupScreen.Output> firstSend(CttuState s, String user) {
    s.reset();
    s.entered = true;
    return new Task(s, Action.ENTER, null).send(user, true);
  }

  public ScreenResponse<CotrtupScreen.Output> receive(
      Action aid, CotrtupScreen.Input input, CttuState s, String user) {
    if (!s.entered) {
      return firstSend(s, user);
    }
    Task t = new Task(s, aid, input);
    boolean validKey = t.validKey();
    if (!validKey) {
      t.err = MSG_INVALID_KEY;
    }
    boolean enter = false;
    if ((aid == Action.PF12
            && EnumSet.of(Mode.SHOW_DETAILS, Mode.CREATE_NEW_RECORD, Mode.DETAILS_NOT_FOUND)
                .contains(s.mode))
        || RESET_ON_NEXT_KEY.contains(s.mode)
        || (s.mode == Mode.CHANGES_BACKED_OUT && Pic.isBlank(s.oldType))) {
      s.reset();
      enter = true;
    }
    if (aid == Action.PF3) {
      boolean fromList = CtliService.PGM.equals(s.fromProgram);
      s.reset();
      s.entered = false;
      return ScreenResponse.<CotrtupScreen.Output>of(
              ScreenId.COTRTUP,
              null,
              ScreenSupport.transfer(
                  user,
                  TRANID,
                  PGM,
                  fromList ? CtliService.TRANID : ScreenSupport.ADMIN_TRANID,
                  fromList ? CtliService.PGM : ScreenSupport.ADMIN_PGM))
          .withNextScreen(fromList ? ScreenId.COTRTLI : ScreenId.COADM01);
    }
    if (enter) {
      return new Task(s, aid, input).send(user, true);
    }
    if (aid == Action.PF4 && s.mode == Mode.CONFIRM_DELETE) {
      s.mode = Mode.START_DELETE;
      t.delete();
    } else if (aid == Action.PF4 && s.mode == Mode.SHOW_DETAILS) {
      s.mode = Mode.CONFIRM_DELETE;
    } else if (aid == Action.PF5 && s.mode == Mode.DETAILS_NOT_FOUND) {
      s.mode = Mode.CREATE_NEW_RECORD;
    } else if (aid == Action.PF5 && s.mode == Mode.CHANGES_OK_NOT_CONFIRMED) {
      t.write();
    } else if (aid == Action.PF12
        && EnumSet.of(Mode.CHANGES_OK_NOT_CONFIRMED, Mode.CONFIRM_DELETE).contains(s.mode)) {
      t.cancel();
    } else if (validKey) {
      t.processInputs();
      t.decide();
    }
    return t.send(user, false);
  }

  private final class Task {
    final CttuState s;
    final Action aid;
    final CotrtupScreen.Input in;
    String err = "";
    boolean inputError;
    boolean typeValid;
    boolean typeError;
    boolean descError;
    boolean noChanges;

    Task(CttuState s, Action aid, CotrtupScreen.Input in) {
      this.s = s;
      this.aid = aid;
      this.in = in == null ? new CotrtupScreen.Input(null, null) : in;
    }

    private void setMsgIfOff(String m) {
      if (err.isEmpty()) {
        err = m;
      }
    }

    /** 0001-CHECK-PFKEYS. */
    boolean validKey() {
      Mode m = s.mode;
      return aid == Action.PF3
          || (aid == Action.ENTER && m != Mode.CONFIRM_DELETE)
          || (aid == Action.PF4 && (m == Mode.SHOW_DETAILS || m == Mode.CONFIRM_DELETE))
          || (aid == Action.PF5
              && (m == Mode.CHANGES_OK_NOT_CONFIRMED
                  || m == Mode.DETAILS_NOT_FOUND
                  || m == Mode.CONFIRM_DELETE
                  || m == Mode.START_DELETE))
          || (aid == Action.PF12
              && (m == Mode.CHANGES_OK_NOT_CONFIRMED
                  || m == Mode.SHOW_DETAILS
                  || m == Mode.DETAILS_NOT_FOUND
                  || m == Mode.CONFIRM_DELETE
                  || m == Mode.CREATE_NEW_RECORD));
    }

    private static String field(String v) {
      return v == null || "*".equals(v.strip()) || Pic.isBlank(v) ? null : v.strip();
    }

    /** 1150-STORE-MAP-IN-NEW + 1200-EDIT-MAP-INPUTS. */
    void processInputs() {
      String inType = field(in.trtypcd());
      boolean sameMissingKey =
          s.mode == Mode.DETAILS_NOT_FOUND && inType != null && inType.equals(s.newType);
      if (!sameMissingKey) {
        String keep = s.newType;
        s.newType = inType;
        s.newDesc = field(in.trtydsc());
        if (s.newType == null && s.mode == Mode.CREATE_NEW_RECORD) {
          s.newType = keep;
        }
      }
      if (sameMissingKey) {
        if (aid != Action.PF5) {
          s.mode = Mode.NOT_FETCHED;
          typeValid = true;
        }
        return;
      }
      if (s.mode != Mode.CREATE_NEW_RECORD && s.mode != Mode.CHANGES_OK_NOT_CONFIRMED) {
        editType();
        if (!typeValid) {
          s.mode = Mode.NOT_FETCHED;
          return;
        }
      }
      if (s.mode == Mode.NOT_FETCHED) {
        return;
      }
      typeValid = true;
      compareOldNew();
      if (noChanges
          || s.mode == Mode.CHANGES_OK_NOT_CONFIRMED
          || s.mode == Mode.CHANGES_OKAYED_AND_DONE) {
        return;
      }
      s.mode = Mode.CHANGES_NOT_OK;
      String d = s.newDesc;
      if (d == null) {
        inputError = true;
        descError = true;
        setMsgIfOff("Transaction Desc must be supplied.");
      } else if (!ScreenSupport.isAlphanumeric(d)) {
        inputError = true;
        descError = true;
        setMsgIfOff("Transaction Desc can have numbers or alphabets only.");
      }
      if (!inputError) {
        s.mode = Mode.CHANGES_OK_NOT_CONFIRMED;
      }
    }

    /** 1210-EDIT-TRANTYPE via 1245-EDIT-NUM-REQD. */
    private void editType() {
      String v = s.newType;
      typeError = true;
      if (v == null) {
        inputError = true;
        setMsgIfOff("Tran Type code must be supplied.");
        return;
      }
      if (!v.chars().allMatch(Character::isDigit)) {
        inputError = true;
        setMsgIfOff("Tran Type code must be numeric.");
        return;
      }
      if (v.chars().allMatch(c -> c == '0')) {
        inputError = true;
        setMsgIfOff("Tran Type code must not be zero.");
        return;
      }
      typeError = false;
      typeValid = true;
      s.newType = v.length() == 1 ? "0" + v : v.substring(v.length() - 2);
    }

    /** 1205-COMPARE-OLD-NEW. */
    private void compareOldNew() {
      String nt = s.newType == null ? "" : s.newType.toUpperCase(Locale.ROOT);
      String ot = s.oldType == null ? "" : s.oldType.toUpperCase(Locale.ROOT);
      String nd = s.newDesc == null ? "" : s.newDesc.strip().toUpperCase(Locale.ROOT);
      String od = s.oldDesc == null ? "" : s.oldDesc.strip().toUpperCase(Locale.ROOT);
      if (nt.equals(ot) && nd.equals(od)) {
        noChanges = true;
        setMsgIfOff(MSG_NO_CHANGES);
      }
    }

    /** 2000-DECIDE-ACTION. */
    void decide() {
      switch (s.mode) {
        case NOT_FETCHED -> {
          if (typeValid) {
            read();
          }
        }
        case SHOW_DETAILS -> {
          if (!inputError && !noChanges) {
            s.mode = Mode.CHANGES_OK_NOT_CONFIRMED;
          }
        }
        case CHANGES_BACKED_OUT -> s.mode = Mode.CHANGES_NOT_OK;
        default -> {}
      }
    }

    /** PF12 while confirming: 2000-DECIDE-ACTION cancel branch. */
    void cancel() {
      if (s.mode == Mode.CONFIRM_DELETE) {
        err = MSG_DELETE_CANCELLED;
        s.mode = Mode.NOT_FETCHED;
      } else {
        err = MSG_UPDATE_CANCELLED;
        s.mode = Mode.CHANGES_BACKED_OUT;
      }
    }

    /** 9000-READ-TRANTYPE. */
    private void read() {
      s.oldType = null;
      s.oldDesc = null;
      Optional<TypeRow> row;
      try {
        row = store.find(s.newType);
      } catch (TrantypeSqlException e) {
        inputError = true;
        typeError = true;
        s.mode = Mode.NOT_FETCHED;
        setMsgIfOff(
            "Error accessing: TRANSACTION_TYPE table. SQLCODE:"
                + ScreenSupport.sqlcode(e.sqlcode())
                + ":"
                + e.getMessage());
        return;
      }
      if (row.isEmpty()) {
        inputError = true;
        typeError = true;
        s.mode = Mode.DETAILS_NOT_FOUND;
        setMsgIfOff(MSG_NOT_FOUND);
        return;
      }
      s.oldType = row.get().type();
      s.oldDesc = Pic.rtrim(row.get().description());
      s.mode = Mode.SHOW_DETAILS;
    }

    /** 9600-WRITE-PROCESSING (+ 9700-INSERT-RECORD on SQLCODE +100). */
    void write() {
      try {
        store.updateDescription(s.newType, s.newDesc);
        s.mode = Mode.CHANGES_OKAYED_AND_DONE;
      } catch (TrantypeSqlException e) {
        if (e.sqlcode() == TrantypeSqlException.NOT_FOUND) {
          insert();
        } else if (e.sqlcode() == TrantypeSqlException.DEADLOCK) {
          inputError = true;
          setMsgIfOff(MSG_LOCK);
          s.mode = Mode.CHANGES_OKAYED_LOCK_ERROR;
        } else {
          err =
              "Error updating: TRANSACTION_TYPE Table. SQLCODE:"
                  + ScreenSupport.sqlcode(e.sqlcode())
                  + ":"
                  + e.getMessage();
          s.mode = Mode.CHANGES_OKAYED_BUT_FAILED;
        }
      }
    }

    private void insert() {
      try {
        store.insert(s.newType, s.newDesc);
        s.mode = Mode.CHANGES_OKAYED_AND_DONE;
      } catch (TrantypeSqlException e) {
        err =
            "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:"
                + ScreenSupport.sqlcode(e.sqlcode())
                + ":"
                + e.getMessage();
        s.mode = Mode.CHANGES_OKAYED_BUT_FAILED;
      }
    }

    /** 9800-DELETE-PROCESSING. */
    void delete() {
      try {
        store.delete(s.oldType);
        s.mode = Mode.DELETE_DONE;
      } catch (TrantypeSqlException e) {
        String prefix =
            e.sqlcode() == TrantypeSqlException.CHILD_ROWS_EXIST
                ? "Please delete associated child records first:"
                : "Delete failed with message:";
        err = prefix + "SQLCODE :" + ScreenSupport.sqlcode(e.sqlcode()) + ":" + e.getMessage();
        s.mode =
            e.sqlcode() == TrantypeSqlException.CHILD_ROWS_EXIST
                ? Mode.SHOW_DETAILS
                : Mode.DELETE_FAILED;
      }
    }

    /** 3000-SEND-MAP. */
    ScreenResponse<CotrtupScreen.Output> send(String user, boolean enter) {
      String type = "";
      String desc = "";
      String info;
      if (enter) {
        err = "";
        info = PROMPT_SEARCH;
      } else {
        switch (s.mode) {
          case NOT_FETCHED -> {}
          case SHOW_DETAILS, CONFIRM_DELETE, DELETE_FAILED, DELETE_DONE, CHANGES_BACKED_OUT -> {
            type = nz(s.oldType);
            desc = nz(s.oldDesc);
          }
          case CHANGES_NOT_OK,
              CHANGES_OK_NOT_CONFIRMED,
              DETAILS_NOT_FOUND,
              INVALID_SEARCH_KEYS,
              CREATE_NEW_RECORD,
              CHANGES_OKAYED_AND_DONE -> {
            type = nz(s.newType);
            desc = nz(s.newDesc);
          }
          default -> {
            type = nz(s.oldType);
            desc = nz(s.oldDesc);
          }
        }
        info = info();
      }
      CotrtupScreen.Output o =
          new CotrtupScreen.Output(
              TRANID,
              ScreenSupport.TITLE01,
              ScreenSupport.date(clock),
              PGM,
              ScreenSupport.TITLE02,
              ScreenSupport.time(clock),
              type,
              desc,
              info,
              err,
              null,
              null,
              null,
              null,
              null);
      ScreenResponse<CotrtupScreen.Output> r =
          ScreenResponse.of(ScreenId.COTRTUP, o, ScreenSupport.context(user, TRANID, PGM));
      r = r.withMessage(ScreenMessage.info(info));
      if (!err.isBlank()) {
        r = r.withMessage(ScreenMessage.error(err.strip()));
      }
      if (typeError) {
        r = r.withFieldError(new FieldError("TRTYPCD", err.strip()));
      }
      if (descError) {
        r = r.withFieldError(new FieldError("TRTYDSC", err.strip()));
      }
      return r;
    }

    private static String nz(String v) {
      return v == null ? "" : v;
    }

    /** 3250-SETUP-INFOMSG. */
    private String info() {
      return switch (s.mode) {
        case NOT_FETCHED, INVALID_SEARCH_KEYS, SHOW_DETAILS -> PROMPT_SEARCH;
        case DETAILS_NOT_FOUND -> PROMPT_CREATE;
        case CHANGES_BACKED_OUT -> Pic.isBlank(s.oldType) ? PROMPT_SEARCH : PROMPT_CHANGES;
        case CHANGES_NOT_OK -> PROMPT_CHANGES;
        case CONFIRM_DELETE -> PROMPT_DELETE;
        case DELETE_FAILED, CHANGES_OKAYED_LOCK_ERROR, CHANGES_OKAYED_BUT_FAILED -> INFORM_FAILURE;
        case DELETE_DONE -> DELETE_SUCCESS;
        case CREATE_NEW_RECORD -> PROMPT_NEWDATA;
        case CHANGES_OK_NOT_CONFIRMED -> PROMPT_CONFIRM;
        case CHANGES_OKAYED_AND_DONE -> UPDATE_SUCCESS;
        default -> PROMPT_SEARCH;
      };
    }
  }
}
