package com.carddemo.online.transaction.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Cotrn02Screen;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.entity.Transaction;
import com.carddemo.online.transaction.file.ReadResult;
import com.carddemo.online.transaction.file.Resp;
import com.carddemo.online.transaction.file.TransactFile;
import com.carddemo.online.transaction.file.XrefFile;
import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CT02 COTRN02C: add a transaction. SEND-TRNADD-SCREEN ends with EXEC CICS RETURN, so the first
 * message sent is the one the user sees (validations stop at the first error).
 */
@Service
public class Cotrn02cService {

  static final String TRANID = "CT02";
  static final String PGM = "COTRN02C";

  private static final String[] DATA = {
    "TTYPCD", "TCATCD", "TRNSRC", "TRNAMT", "TDESC", "TORIGDT", "TPROCDT", "MID", "MNAME", "MCITY",
    "MZIP"
  };

  private final TransactFile transact;
  private final XrefFile xref;
  private final BusinessClock clock;

  public Cotrn02cService(TransactFile transact, XrefFile xref, BusinessClock clock) {
    this.transact = transact;
    this.xref = xref;
    this.clock = clock;
  }

  @Transactional
  public ProgramResult<Cotrn02Screen.Output> run(
      ConversationContext ctx, CommareaExtension ext, Action aid, Map<String, String> input) {
    MapBuffer map = new MapBuffer(ScreenId.COTRN02, Cotrn02Screen.Output.class, clock, true);
    // COTRN02C MAIN-PARA
    try {
      if (ctx.programContext() != ProgramContext.REENTER) {
        ctx = ctx.reenter();
        map.lowValues();
        map.cursor("ACTIDIN");
        if (!CobolFmt.blank(ext.selected())) {
          map.set("CARDNIN", ext.selected());
          processEnterKey(map);
        }
        map.send();
      } else {
        map.receive(input);
        switch (aid) {
          case ENTER -> processEnterKey(map);
          case PF3 -> {
            String to = CobolFmt.blank(ctx.fromProgram()) ? "COMEN01C" : ctx.fromProgram();
            ConversationContext next = Navigation.xctl(ctx, TRANID, PGM, to);
            return new ProgramResult<>(
                MapBuffer.xctl(ScreenId.COTRN02, Navigation.screenOf(next.toProgram()), next),
                withAid(ext, aid));
          }
          case PF4 -> {
            initializeAllFields(map);
            map.send();
          }
          case PF5 -> copyLastTranData(map);
          default -> {
            map.err(true);
            map.message(MapBuffer.MSG_INVALID_KEY);
            map.send();
          }
        }
      }
    } catch (MapBuffer.ReturnToCics end) {
      // EXEC CICS RETURN TRANSID(CT02) issued by SEND-TRNADD-SCREEN
    }
    return new ProgramResult<>(
        map.response(Cotrn02Screen.Output.class, ctx), Cotrn01cService.noAid(ext));
  }

  // COTRN02C PROCESS-ENTER-KEY
  private void processEnterKey(MapBuffer map) {
    validateInputKeyFields(map);
    validateInputDataFields(map);
    String confirm = map.get("CONFIRM");
    switch (confirm) {
      case "Y", "y" -> addTransaction(map);
      case "N", "n" -> {
        map.error("Confirm to add this transaction...", "CONFIRM");
        map.send();
      }
      default -> {
        if (CobolFmt.blank(confirm)) {
          map.error("Confirm to add this transaction...", "CONFIRM");
        } else {
          map.error("Invalid value. Valid values are (Y/N)...", "CONFIRM");
        }
        map.send();
      }
    }
  }

  // COTRN02C VALIDATE-INPUT-KEY-FIELDS
  private void validateInputKeyFields(MapBuffer map) {
    String actid = map.get("ACTIDIN");
    String card = map.get("CARDNIN");
    if (!CobolFmt.blank(actid)) {
      if (!CobolFmt.isNumeric(CobolFmt.pad(actid, 11))) {
        map.error("Account ID must be Numeric...", "ACTIDIN");
        map.send();
      }
      long acctId = Long.parseLong(actid.strip());
      map.set("ACTIDIN", CobolFmt.digits(acctId, 11));
      ReadResult<CardXref> r = xref.readByAcct(acctId);
      if (r.resp() == Resp.NOTFND) {
        map.error("Account ID NOT found...", "ACTIDIN");
        map.send();
      } else if (r.resp() != Resp.NORMAL) {
        map.error("Unable to lookup Acct in XREF AIX file...", "ACTIDIN");
        map.send();
      }
      map.set("CARDNIN", r.record().getXrefCardNum());
    } else if (!CobolFmt.blank(card)) {
      if (!CobolFmt.isNumeric(CobolFmt.pad(card, 16))) {
        map.error("Card Number must be Numeric...", "CARDNIN");
        map.send();
      }
      String cardNum = CobolFmt.digits(Long.parseLong(card.strip()), 16);
      map.set("CARDNIN", cardNum);
      ReadResult<CardXref> r = xref.readByCard(cardNum);
      if (r.resp() == Resp.NOTFND) {
        map.error("Card Number NOT found...", "CARDNIN");
        map.send();
      } else if (r.resp() != Resp.NORMAL) {
        map.error("Unable to lookup Card # in XREF file...", "CARDNIN");
        map.send();
      }
      map.set("ACTIDIN", CobolFmt.digits(r.record().getXrefAcctId(), 11));
    } else {
      map.error("Account or Card Number must be entered...", "ACTIDIN");
      map.send();
    }
  }

  // COTRN02C VALIDATE-INPUT-DATA-FIELDS
  private void validateInputDataFields(MapBuffer map) {
    String[][] required = {
      {"TTYPCD", "Type CD can NOT be empty..."},
      {"TCATCD", "Category CD can NOT be empty..."},
      {"TRNSRC", "Source can NOT be empty..."},
      {"TDESC", "Description can NOT be empty..."},
      {"TRNAMT", "Amount can NOT be empty..."},
      {"TORIGDT", "Orig Date can NOT be empty..."},
      {"TPROCDT", "Proc Date can NOT be empty..."},
      {"MID", "Merchant ID can NOT be empty..."},
      {"MNAME", "Merchant Name can NOT be empty..."},
      {"MCITY", "Merchant City can NOT be empty..."},
      {"MZIP", "Merchant Zip can NOT be empty..."}
    };
    for (String[] r : required) {
      if (CobolFmt.blank(map.get(r[0]))) {
        map.error(r[1], r[0]);
        map.send();
      }
    }
    if (!CobolFmt.isNumeric(CobolFmt.pad(map.get("TTYPCD"), 2))) {
      map.error("Type CD must be Numeric...", "TTYPCD");
      map.send();
    }
    if (!CobolFmt.isNumeric(CobolFmt.pad(map.get("TCATCD"), 4))) {
      map.error("Category CD must be Numeric...", "TCATCD");
      map.send();
    }
    String amt = CobolFmt.pad(map.get("TRNAMT"), 12);
    if ((amt.charAt(0) != '-' && amt.charAt(0) != '+')
        || !CobolFmt.isNumeric(amt.substring(1, 9))
        || amt.charAt(9) != '.'
        || !CobolFmt.isNumeric(amt.substring(10, 12))) {
      map.error("Amount should be in format -99999999.99", "TRNAMT");
      map.send();
    }
    if (!dateFormat(map.get("TORIGDT"))) {
      map.error("Orig Date should be in format YYYY-MM-DD", "TORIGDT");
      map.send();
    }
    if (!dateFormat(map.get("TPROCDT"))) {
      map.error("Proc Date should be in format YYYY-MM-DD", "TPROCDT");
      map.send();
    }
    map.set("TRNAMT", CobolFmt.editSigned(amount(map), 8));
    if (!CeeDays.accepted(CobolFmt.pad(map.get("TORIGDT"), 10))) {
      map.error("Orig Date - Not a valid date...", "TORIGDT");
      map.send();
    }
    if (!CeeDays.accepted(CobolFmt.pad(map.get("TPROCDT"), 10))) {
      map.error("Proc Date - Not a valid date...", "TPROCDT");
      map.send();
    }
    if (!CobolFmt.isNumeric(CobolFmt.pad(map.get("MID"), 9))) {
      map.error("Merchant ID must be Numeric...", "MID");
      map.send();
    }
  }

  private static boolean dateFormat(String v) {
    String d = CobolFmt.pad(v, 10);
    return CobolFmt.isNumeric(d.substring(0, 4))
        && d.charAt(4) == '-'
        && CobolFmt.isNumeric(d.substring(5, 7))
        && d.charAt(7) == '-'
        && CobolFmt.isNumeric(d.substring(8, 10));
  }

  /** COMPUTE WS-TRAN-AMT-N (S9(9)V99) = FUNCTION NUMVAL-C(TRNAMTI). */
  private static BigDecimal amount(MapBuffer map) {
    return CobolMath.store(CobolFmt.numvalC(map.get("TRNAMT")), 9, 2, CobolMath.Mode.TRUNCATE);
  }

  // COTRN02C ADD-TRANSACTION
  private void addTransaction(MapBuffer map) {
    String id = TranIdAllocator.next(transact);
    if (id == null) {
      map.error("Unable to lookup Transaction...", "ACTIDIN");
      map.send();
    }
    Transaction t = new Transaction();
    t.setTranId(id);
    t.setTranTypeCd(CobolFmt.pad(map.get("TTYPCD"), 2));
    t.setTranCatCd(Integer.parseInt(CobolFmt.pad(map.get("TCATCD"), 4)));
    t.setTranSource(CobolFmt.pad(map.get("TRNSRC"), 10));
    t.setTranDesc(CobolFmt.pad(map.get("TDESC"), 100));
    t.setTranAmt(amount(map));
    t.setTranCardNum(CobolFmt.pad(map.get("CARDNIN"), 16));
    t.setTranMerchantId(Integer.parseInt(CobolFmt.pad(map.get("MID"), 9)));
    t.setTranMerchantName(CobolFmt.pad(map.get("MNAME"), 50));
    t.setTranMerchantCity(CobolFmt.pad(map.get("MCITY"), 50));
    t.setTranMerchantZip(CobolFmt.pad(map.get("MZIP"), 10));
    t.setTranOrigTs(CobolFmt.pad(map.get("TORIGDT"), 26));
    t.setTranProcTs(CobolFmt.pad(map.get("TPROCDT"), 26));
    t.setFiller(CobolFmt.pad("", 20));
    // COTRN02C WRITE-TRANSACT-FILE
    switch (transact.write(t)) {
      case NORMAL -> {
        initializeAllFields(map);
        map.green();
        map.message("Transaction added successfully. " + " Your Tran ID is " + id.strip() + ".");
        map.send();
      }
      case DUPREC, DUPKEY -> {
        map.error("Tran ID already exist...", "ACTIDIN");
        map.send();
      }
      default -> {
        map.error("Unable to Add Transaction...", "ACTIDIN");
        map.send();
      }
    }
  }

  // COTRN02C COPY-LAST-TRAN-DATA
  private void copyLastTranData(MapBuffer map) {
    validateInputKeyFields(map);
    TransactBrowse browse = new TransactBrowse(transact);
    browse.startbr(null);
    ReadResult<Transaction> last = browse.readPrev();
    browse.endbr();
    if (last.resp() == Resp.ERROR) {
      map.error("Unable to lookup Transaction...", "ACTIDIN");
      map.send();
    }
    if (!map.err()) {
      Transaction t = last.record();
      if (t == null) {
        map.spaces(DATA);
      } else {
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
      }
    }
    processEnterKey(map);
  }

  // COTRN02C INITIALIZE-ALL-FIELDS
  private static void initializeAllFields(MapBuffer map) {
    map.cursor("ACTIDIN");
    map.spaces("ACTIDIN", "CARDNIN", "CONFIRM");
    map.spaces(DATA);
    map.message("");
  }

  static CommareaExtension withAid(CommareaExtension ext, Action aid) {
    return new CommareaExtension(
        ext.trnidFirst(),
        ext.trnidLast(),
        ext.pageNum(),
        ext.nextPage(),
        ext.selFlg(),
        ext.selected(),
        aid.name(),
        ext.lastScreen());
  }
}
