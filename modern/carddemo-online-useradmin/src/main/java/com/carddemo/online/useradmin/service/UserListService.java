package com.carddemo.online.useradmin.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cousr00Screen;
import com.carddemo.online.useradmin.session.UseradminState;
import com.carddemo.online.useradmin.session.UseradminState.Row;
import com.carddemo.online.useradmin.store.UserRow;
import com.carddemo.online.useradmin.store.UserStore;
import com.carddemo.online.useradmin.support.Fields;
import com.carddemo.online.useradmin.support.Messages;
import com.carddemo.online.useradmin.support.ScreenHeader;
import java.util.List;
import org.springframework.stereotype.Service;

/** COUSR00C (CU00): list users, 10 per page, with U/D selection. */
@Service
public class UserListService {

  static final ScreenId ME = ScreenId.COUSR00;
  static final String USRIDIN = "USRIDIN";

  /** LOW-VALUES key: every record key is {@code >=} it. */
  private static final String LOW_VALUES = "";

  /** HIGH-VALUES key: no record key is {@code >=} it. */
  private static final String HIGH_VALUES = "\uffff".repeat(8);

  /** Skip record + 10 rows + look-ahead record. */
  private static final int BROWSE_WINDOW = UseradminState.ROWS + 2;

  private final UserStore store;
  private final BusinessClock clock;

  public UserListService(UserStore store, BusinessClock clock) {
    this.store = store;
    this.clock = clock;
  }

  /** First send of COUSR0A ({@code NOT CDEMO-PGM-REENTER}). */
  public ScreenResponse<Cousr00Screen.Output> send(ConversationContext ctx, UseradminState state) {
    Task t = new Task(state, null, true);
    t.processEnterKey(Action.ENTER);
    return t.reply.toResponse(ME, t.output(), ctx, state);
  }

  /** RECEIVE MAP COUSR0A + EVALUATE EIBAID. */
  public ScreenResponse<Cousr00Screen.Output> receive(
      Action action, Cousr00Screen.Input input, ConversationContext ctx, UseradminState state) {
    Task t = new Task(state, input, false);
    switch (action) {
      case ENTER -> t.processEnterKey(action);
      case PF3 -> t.reply.transferTo(ScreenId.COADM01);
      case PF7 -> t.processPf7Key();
      case PF8 -> t.processPf8Key();
      default -> t.reply.error(Messages.INVALID_KEY, USRIDIN);
    }
    return t.reply.toResponse(ME, t.output(), ctx, state);
  }

  /** Working storage and map of one CU00 task. */
  private final class Task {
    final Reply reply = new Reply();
    final UseradminState st;
    final Row[] rows;
    final String[] sel = new String[UseradminState.ROWS];
    String usridin;
    boolean eof;
    boolean notFound;
    List<UserRow> browse = List.of();
    int cursor;

    Task(UseradminState st, Cousr00Screen.Input in, boolean firstSend) {
      this.st = st;
      if (firstSend || in == null) {
        rows = firstSend ? blankRows() : st.rows();
        usridin = "";
        java.util.Arrays.fill(sel, "");
        if (firstSend) {
          st.pageNumDisplay("");
        }
      } else {
        rows = st.rows();
        usridin = Fields.received(in.usridin(), 8);
        String[] s = {
          in.sel0001(), in.sel0002(), in.sel0003(), in.sel0004(), in.sel0005(),
          in.sel0006(), in.sel0007(), in.sel0008(), in.sel0009(), in.sel0010()
        };
        for (int i = 0; i < s.length; i++) {
          sel[i] = Fields.received(s[i], 1);
        }
      }
    }

    private static Row[] blankRows() {
      Row[] r = new Row[UseradminState.ROWS];
      java.util.Arrays.fill(r, Row.BLANK);
      return r;
    }

    void processEnterKey(Action aid) {
      st.selFlag("");
      st.selected("");
      for (int i = 0; i < UseradminState.ROWS; i++) {
        if (!Fields.isBlank(sel[i])) {
          st.selFlag(sel[i]);
          st.selected(rows[i].userId());
          break;
        }
      }
      if (!Fields.isBlank(st.selFlag()) && !Fields.isBlank(st.selected())) {
        switch (st.selFlag()) {
          case "U", "u" -> {
            reply.transferTo(ScreenId.COUSR02);
            return;
          }
          case "D", "d" -> {
            reply.transferTo(ScreenId.COUSR03);
            return;
          }
          default -> reply.warning(Messages.INVALID_SELECTION);
        }
      }
      String key = Fields.isBlank(usridin) ? LOW_VALUES : Fields.pad(usridin, 8);
      st.pageNum(0);
      processPageForward(aid, key);
      if (!reply.errorFlag()) {
        usridin = "";
      }
    }

    void processPf7Key() {
      String key = Fields.isBlank(st.usridFirst()) ? LOW_VALUES : st.usridFirst();
      st.nextPage(true);
      if (st.pageNum() > 1) {
        processPageBackward(key);
      } else {
        reply.info(Messages.ALREADY_TOP);
      }
    }

    void processPf8Key() {
      String key = Fields.isBlank(st.usridLast()) ? HIGH_VALUES : st.usridLast();
      if (st.nextPage()) {
        processPageForward(Action.PF8, key);
      } else {
        reply.info(Messages.ALREADY_BOTTOM);
      }
    }

    void processPageForward(Action aid, String key) {
      try {
        browse = store.browseForward(key, BROWSE_WINDOW);
      } catch (RuntimeException e) {
        reply.error(Messages.UNABLE_TO_LOOKUP, USRIDIN);
        return;
      }
      startbr();
      if (aid != Action.ENTER && aid != Action.PF7 && aid != Action.PF3) {
        read(Messages.REACHED_BOTTOM);
      }
      if (!eof && !reply.errorFlag()) {
        java.util.Arrays.fill(rows, Row.BLANK);
      }
      int idx = 1;
      while (idx < 11 && !eof && !reply.errorFlag()) {
        UserRow r = read(Messages.REACHED_BOTTOM);
        if (r != null) {
          populate(idx, r);
          idx++;
        }
      }
      if (!eof && !reply.errorFlag()) {
        st.pageNum(st.pageNum() + 1);
        st.nextPage(read(Messages.REACHED_BOTTOM) != null);
      } else {
        st.nextPage(false);
        if (idx > 1) {
          st.pageNum(st.pageNum() + 1);
        }
      }
      st.pageNumDisplay(String.format("%08d", st.pageNum()));
      usridin = "";
    }

    void processPageBackward(String key) {
      try {
        List<UserRow> probe = store.browseForward(key, 1);
        browse =
            probe.isEmpty() ? List.of() : store.browseBackward(probe.get(0).id(), BROWSE_WINDOW);
      } catch (RuntimeException e) {
        reply.error(Messages.UNABLE_TO_LOOKUP, USRIDIN);
        return;
      }
      startbr();
      read(Messages.REACHED_TOP);
      if (!eof && !reply.errorFlag()) {
        java.util.Arrays.fill(rows, Row.BLANK);
      }
      int idx = 10;
      while (idx > 0 && !eof && !reply.errorFlag()) {
        UserRow r = read(Messages.REACHED_TOP);
        if (r != null) {
          populate(idx, r);
          idx--;
        }
      }
      if (!eof && !reply.errorFlag()) {
        UserRow r = read(Messages.REACHED_TOP);
        if (st.nextPage()) {
          if (r != null && st.pageNum() > 1) {
            st.pageNum(st.pageNum() - 1);
          } else {
            st.pageNum(1);
          }
        }
      }
      st.pageNumDisplay(String.format("%08d", st.pageNum()));
    }

    /** STARTBR: NOTFND when no record key is {@code >=} the RIDFLD. */
    private void startbr() {
      cursor = 0;
      if (browse.isEmpty()) {
        notFound = true;
        eof = true;
        reply.info(Messages.AT_TOP);
      }
    }

    /** READNEXT / READPREV: ENDFILE sets USER-SEC-EOF; no active browse is RESP OTHER. */
    private UserRow read(String endOfFileMessage) {
      if (notFound) {
        reply.error(Messages.UNABLE_TO_LOOKUP, USRIDIN);
        return null;
      }
      if (cursor < browse.size()) {
        return browse.get(cursor++);
      }
      eof = true;
      reply.info(endOfFileMessage);
      return null;
    }

    private void populate(int idx, UserRow r) {
      rows[idx - 1] = new Row(r.id(), r.firstName(), r.lastName(), r.type());
      if (idx == 1) {
        st.usridFirst(r.id());
      }
      if (idx == 10) {
        st.usridLast(r.id());
      }
    }

    Cousr00Screen.Output output() {
      st.rows(rows);
      ScreenHeader h = ScreenHeader.of(ME.transactionId(), ME.program(), clock);
      String[] f = new String[5 * UseradminState.ROWS];
      for (int i = 0; i < UseradminState.ROWS; i++) {
        f[5 * i] = Fields.rtrim(sel[i]);
        f[5 * i + 1] = Fields.rtrim(rows[i].userId());
        f[5 * i + 2] = Fields.rtrim(rows[i].firstName());
        f[5 * i + 3] = Fields.rtrim(rows[i].lastName());
        f[5 * i + 4] = Fields.rtrim(rows[i].userType());
      }
      return new Cousr00Screen.Output(
          h.trnname(),
          Fields.rtrim(h.title01()),
          h.curdate(),
          h.pgmname(),
          Fields.rtrim(h.title02()),
          h.curtime(),
          st.pageNumDisplay(),
          Fields.rtrim(usridin),
          f[0],
          f[1],
          f[2],
          f[3],
          f[4],
          f[5],
          f[6],
          f[7],
          f[8],
          f[9],
          f[10],
          f[11],
          f[12],
          f[13],
          f[14],
          f[15],
          f[16],
          f[17],
          f[18],
          f[19],
          f[20],
          f[21],
          f[22],
          f[23],
          f[24],
          f[25],
          f[26],
          f[27],
          f[28],
          f[29],
          f[30],
          f[31],
          f[32],
          f[33],
          f[34],
          f[35],
          f[36],
          f[37],
          f[38],
          f[39],
          f[40],
          f[41],
          f[42],
          f[43],
          f[44],
          f[45],
          f[46],
          f[47],
          f[48],
          f[49],
          reply.message());
    }
  }
}
