package com.carddemo.online.transaction.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Cotrn01Screen;
import com.carddemo.domain.entity.Transaction;
import com.carddemo.online.transaction.file.ReadResult;
import com.carddemo.online.transaction.file.TransactFile;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CT01 COTRN01C: view one transaction (PF4 clear, PF5 back to the list). */
@Service
public class Cotrn01cService {

  static final String TRANID = "CT01";
  static final String PGM = "COTRN01C";

  private static final String[] DETAIL = {
    "TRNID", "CARDNUM", "TTYPCD", "TCATCD", "TRNSRC", "TRNAMT", "TDESC", "TORIGDT", "TPROCDT",
    "MID", "MNAME", "MCITY", "MZIP"
  };

  private final TransactFile transact;
  private final BusinessClock clock;

  public Cotrn01cService(TransactFile transact, BusinessClock clock) {
    this.transact = transact;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ProgramResult<Cotrn01Screen.Output> run(
      ConversationContext ctx, CommareaExtension ext, Action aid, Map<String, String> input) {
    MapBuffer map = new MapBuffer(ScreenId.COTRN01, Cotrn01Screen.Output.class, clock, false);
    // COTRN01C MAIN-PARA
    if (ctx.programContext() != ProgramContext.REENTER) {
      ctx = ctx.reenter();
      map.lowValues();
      map.cursor("TRNIDIN");
      if (!CobolFmt.blank(ext.selected())) {
        map.set("TRNIDIN", ext.selected());
        processEnterKey(map);
      }
      map.send();
    } else {
      map.receive(input);
      switch (aid) {
        case ENTER -> processEnterKey(map);
        case PF3 -> {
          String to = CobolFmt.blank(ctx.fromProgram()) ? "COMEN01C" : ctx.fromProgram();
          return xctl(ctx, ext, to, aid);
        }
        case PF4 -> {
          initializeAllFields(map);
          map.send();
        }
        case PF5 -> {
          return xctl(ctx, ext, "COTRN00C", aid);
        }
        default -> {
          map.err(true);
          map.message(MapBuffer.MSG_INVALID_KEY);
          map.send();
        }
      }
    }
    return new ProgramResult<>(map.response(Cotrn01Screen.Output.class, ctx), noAid(ext));
  }

  // COTRN01C PROCESS-ENTER-KEY
  private void processEnterKey(MapBuffer map) {
    if (CobolFmt.blank(map.get("TRNIDIN"))) {
      map.error("Tran ID can NOT be empty...", "TRNIDIN");
      map.send();
    } else {
      map.cursor("TRNIDIN");
    }
    Transaction t = null;
    if (!map.err()) {
      map.spaces(DETAIL);
      ReadResult<Transaction> r = transact.read(CobolFmt.pad(map.get("TRNIDIN"), 16));
      switch (r.resp()) {
        case NORMAL -> t = r.record();
        case NOTFND -> {
          map.error("Transaction ID NOT found...", "TRNIDIN");
          map.send();
        }
        default -> {
          map.error("Unable to lookup Transaction...", "TRNIDIN");
          map.send();
        }
      }
    }
    if (!map.err() && t != null) {
      map.set("TRNID", t.getTranId());
      map.set("CARDNUM", t.getTranCardNum());
      map.set("TTYPCD", t.getTranTypeCd());
      map.set("TCATCD", CobolFmt.digits(t.getTranCatCd(), 4));
      map.set("TRNSRC", t.getTranSource());
      map.set("TRNAMT", CobolFmt.editSigned(t.getTranAmt(), 8));
      map.set("TDESC", t.getTranDesc());
      map.set("TORIGDT", t.getTranOrigTs());
      map.set("TPROCDT", t.getTranProcTs());
      map.set("MID", CobolFmt.digits(t.getTranMerchantId(), 9));
      map.set("MNAME", t.getTranMerchantName());
      map.set("MCITY", t.getTranMerchantCity());
      map.set("MZIP", t.getTranMerchantZip());
      map.send();
    }
  }

  // COTRN01C INITIALIZE-ALL-FIELDS
  private static void initializeAllFields(MapBuffer map) {
    map.cursor("TRNIDIN");
    map.spaces("TRNIDIN");
    map.spaces(DETAIL);
    map.message("");
  }

  // COTRN01C RETURN-TO-PREV-SCREEN
  private static ProgramResult<Cotrn01Screen.Output> xctl(
      ConversationContext ctx, CommareaExtension ext, String to, Action aid) {
    ConversationContext next = Navigation.xctl(ctx, TRANID, PGM, to);
    CommareaExtension keep =
        new CommareaExtension(
            ext.trnidFirst(),
            ext.trnidLast(),
            ext.pageNum(),
            ext.nextPage(),
            ext.selFlg(),
            ext.selected(),
            aid.name(),
            ext.lastScreen());
    return new ProgramResult<>(
        MapBuffer.xctl(ScreenId.COTRN01, Navigation.screenOf(next.toProgram()), next), keep);
  }

  static CommareaExtension noAid(CommareaExtension ext) {
    return new CommareaExtension(
        ext.trnidFirst(),
        ext.trnidLast(),
        ext.pageNum(),
        ext.nextPage(),
        ext.selFlg(),
        ext.selected(),
        null,
        ext.lastScreen());
  }
}
