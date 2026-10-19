package com.carddemo.online.transaction.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Cotrn00Screen;
import com.carddemo.domain.entity.Transaction;
import com.carddemo.online.transaction.file.ReadResult;
import com.carddemo.online.transaction.file.Resp;
import com.carddemo.online.transaction.file.TransactFile;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CT00 COTRN00C: transaction list, 10 rows per page, PF7/PF8 paging, 'S' selects (CT01). */
@Service
public class Cotrn00cService {

  static final String TRANID = "CT00";
  static final String PGM = "COTRN00C";

  private final TransactFile transact;
  private final BusinessClock clock;

  public Cotrn00cService(TransactFile transact, BusinessClock clock) {
    this.transact = transact;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ProgramResult<Cotrn00Screen.Output> run(
      ConversationContext ctx, CommareaExtension ext, Action aid, Map<String, String> input) {
    return new Task(ctx, ext, aid, input).main();
  }

  /** One CICS task: working storage of COTRN00C. */
  private final class Task {
    private final MapBuffer map =
        new MapBuffer(ScreenId.COTRN00, Cotrn00Screen.Output.class, clock, false);
    private final TransactBrowse browse = new TransactBrowse(transact);
    private final Map<String, String> input;
    private final Map<String, String> previous;
    private ConversationContext ctx;
    private Action eibaid;
    private boolean eof;
    private boolean sendErase = true;
    private String tranId = "";
    private String trnidFirst;
    private String trnidLast;
    private long pageNum;
    private boolean nextPage;
    private String selFlg;
    private String selected;
    private ScreenId xctlTo;

    Task(ConversationContext ctx, CommareaExtension ext, Action aid, Map<String, String> input) {
      this.ctx = ctx;
      this.input = input;
      this.eibaid = aid;
      this.previous = ext.lastScreen();
      this.trnidFirst = ext.trnidFirst();
      this.trnidLast = ext.trnidLast();
      this.pageNum = ext.pageNum();
      this.nextPage = ext.nextPage();
      this.selFlg = ext.selFlg();
      this.selected = ext.selected();
      if (eibaid == null) {
        eibaid = ext.xctlAid() == null ? Action.ENTER : Action.valueOf(ext.xctlAid());
      }
    }

    // COTRN00C MAIN-PARA
    ProgramResult<Cotrn00Screen.Output> main() {
      map.cursor("TRNIDIN");
      if (ctx.programContext() != ProgramContext.REENTER) {
        ctx = ctx.reenter();
        map.lowValues();
        processEnterKey();
        if (xctlTo == null) {
          sendTrnlstScreen();
        }
      } else {
        map.receive(input);
        switch (eibaid) {
          case ENTER -> processEnterKey();
          case PF3 -> returnToPrevScreen("COMEN01C");
          case PF7 -> processPf7Key();
          case PF8 -> processPf8Key();
          default -> {
            map.error(MapBuffer.MSG_INVALID_KEY, "TRNIDIN");
            sendTrnlstScreen();
          }
        }
      }
      CommareaExtension ext =
          new CommareaExtension(
              trnidFirst,
              trnidLast,
              pageNum,
              nextPage,
              selFlg,
              selected,
              xctlTo == null ? null : eibaid.name(),
              map.sent() ? map.shown() : previous);
      if (xctlTo != null) {
        return new ProgramResult<>(MapBuffer.xctl(ScreenId.COTRN00, xctlTo, ctx), ext);
      }
      return new ProgramResult<>(map.response(Cotrn00Screen.Output.class, ctx), ext);
    }

    // COTRN00C PROCESS-ENTER-KEY
    private void processEnterKey() {
      selFlg = "";
      selected = "";
      for (int i = 1; i <= 10; i++) {
        String sel = map.get("SEL%04d".formatted(i));
        if (!CobolFmt.blank(sel)) {
          selFlg = sel;
          selected = previous.getOrDefault("TRNID%02d".formatted(i), "");
          break;
        }
      }
      if (!CobolFmt.blank(selFlg) && !CobolFmt.blank(selected)) {
        if ("S".equals(selFlg) || "s".equals(selFlg)) {
          returnToPrevScreen("COTRN01C");
          return;
        }
        map.message("Invalid selection. Valid value is S");
        map.cursor("TRNIDIN");
      }
      String trnidin = map.get("TRNIDIN");
      if (CobolFmt.blank(trnidin)) {
        tranId = "";
      } else if (CobolFmt.isNumeric(trnidin)) {
        tranId = trnidin;
      } else {
        map.error("Tran ID must be Numeric ...", "TRNIDIN");
        sendTrnlstScreen();
      }
      map.cursor("TRNIDIN");
      pageNum = 0;
      processPageForward();
      if (!map.err()) {
        map.set("TRNIDIN", " ");
      }
    }

    // COTRN00C PROCESS-PF7-KEY
    private void processPf7Key() {
      tranId = CobolFmt.blank(trnidFirst) ? "" : trnidFirst;
      nextPage = true;
      map.cursor("TRNIDIN");
      if (pageNum > 1) {
        processPageBackward();
      } else {
        map.message("You are already at the top of the page...");
        sendErase = false;
        sendTrnlstScreen();
      }
    }

    // COTRN00C PROCESS-PF8-KEY
    private void processPf8Key() {
      tranId = CobolFmt.blank(trnidLast) ? null : trnidLast;
      map.cursor("TRNIDIN");
      if (nextPage) {
        processPageForward();
      } else {
        map.message("You are already at the bottom of the page...");
        sendErase = false;
        sendTrnlstScreen();
      }
    }

    // COTRN00C PROCESS-PAGE-FORWARD
    private void processPageForward() {
      startbr();
      if (map.err()) {
        return;
      }
      if (!Set.of(Action.ENTER, Action.PF7, Action.PF3).contains(eibaid)) {
        readNext();
      }
      if (!eof && !map.err()) {
        for (int idx = 1; idx <= 10; idx++) {
          initializeTranData(idx);
        }
      }
      int idx = 1;
      while (!(idx >= 11 || eof || map.err())) {
        Transaction t = readNext();
        if (!eof && !map.err()) {
          populateTranData(idx, t);
          idx++;
        }
      }
      if (!eof && !map.err()) {
        pageNum++;
        readNext();
        nextPage = !eof && !map.err();
      } else {
        nextPage = false;
        if (idx > 1) {
          pageNum++;
        }
      }
      browse.endbr();
      map.set("PAGENUM", CobolFmt.digits(pageNum, 8));
      map.set("TRNIDIN", " ");
      sendTrnlstScreen();
    }

    // COTRN00C PROCESS-PAGE-BACKWARD
    private void processPageBackward() {
      startbr();
      if (map.err()) {
        return;
      }
      if (!Set.of(Action.ENTER, Action.PF8).contains(eibaid)) {
        readPrev();
      }
      if (!eof && !map.err()) {
        for (int idx = 1; idx <= 10; idx++) {
          initializeTranData(idx);
        }
      }
      int idx = 10;
      while (!(idx <= 0 || eof || map.err())) {
        Transaction t = readPrev();
        if (!eof && !map.err()) {
          populateTranData(idx, t);
          idx--;
        }
      }
      if (!eof && !map.err()) {
        readPrev();
        if (nextPage) {
          if (!eof && !map.err() && pageNum > 1) {
            pageNum--;
          } else {
            pageNum = 1;
          }
        }
      }
      browse.endbr();
      map.set("PAGENUM", CobolFmt.digits(pageNum, 8));
      sendTrnlstScreen();
    }

    // COTRN00C POPULATE-TRAN-DATA
    private void populateTranData(int idx, Transaction t) {
      String ts = CobolFmt.pad(t.getTranOrigTs(), 26);
      String date = ts.substring(5, 7) + "/" + ts.substring(8, 10) + "/" + ts.substring(2, 4);
      map.set("TRNID%02d".formatted(idx), t.getTranId());
      map.set("TDATE%02d".formatted(idx), date);
      map.set("TDESC%02d".formatted(idx), t.getTranDesc());
      map.set("TAMT%03d".formatted(idx), CobolFmt.editSigned(t.getTranAmt(), 8));
      if (idx == 1) {
        trnidFirst = t.getTranId();
      }
      if (idx == 10) {
        trnidLast = t.getTranId();
      }
    }

    // COTRN00C INITIALIZE-TRAN-DATA
    private void initializeTranData(int idx) {
      map.spaces(
          "TRNID%02d".formatted(idx),
          "TDATE%02d".formatted(idx),
          "TDESC%02d".formatted(idx),
          "TAMT%03d".formatted(idx));
    }

    // COTRN00C RETURN-TO-PREV-SCREEN (and the XCTL to COTRN01C of PROCESS-ENTER-KEY)
    private void returnToPrevScreen(String toProgram) {
      ctx = Navigation.xctl(ctx, TRANID, PGM, toProgram);
      xctlTo = Navigation.screenOf(ctx.toProgram());
    }

    // COTRN00C SEND-TRNLST-SCREEN
    private void sendTrnlstScreen() {
      map.send(sendErase, previous);
    }

    // COTRN00C STARTBR-TRANSACT-FILE
    private void startbr() {
      Resp r = browse.startbr(tranId);
      if (r == Resp.NOTFND) {
        eof = true;
        map.message("You are at the top of the page...");
        map.cursor("TRNIDIN");
        sendTrnlstScreen();
      } else if (r != Resp.NORMAL) {
        map.error("Unable to lookup transaction...", "TRNIDIN");
        sendTrnlstScreen();
      }
    }

    // COTRN00C READNEXT-TRANSACT-FILE
    private Transaction readNext() {
      return afterRead(browse.readNext(), "You have reached the bottom of the page...");
    }

    // COTRN00C READPREV-TRANSACT-FILE
    private Transaction readPrev() {
      return afterRead(browse.readPrev(), "You have reached the top of the page...");
    }

    private Transaction afterRead(ReadResult<Transaction> r, String endMessage) {
      switch (r.resp()) {
        case NORMAL -> {
          return r.record();
        }
        case ENDFILE -> {
          eof = true;
          map.message(endMessage);
          map.cursor("TRNIDIN");
          sendTrnlstScreen();
        }
        default -> {
          map.error("Unable to lookup transaction...", "TRNIDIN");
          sendTrnlstScreen();
        }
      }
      return null;
    }
  }
}
