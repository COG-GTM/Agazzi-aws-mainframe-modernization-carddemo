package com.carddemo.online.transaction.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Cobil00Screen;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.entity.Transaction;
import com.carddemo.online.transaction.file.AccountFile;
import com.carddemo.online.transaction.file.ReadResult;
import com.carddemo.online.transaction.file.Resp;
import com.carddemo.online.transaction.file.TransactFile;
import com.carddemo.online.transaction.file.XrefFile;
import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * CB00 COBIL00C: pay the whole current balance of an account. Creates the payment transaction (type
 * 02, category 2) and sets ACCT-CURR-BAL to zero in the same unit of work; the account is read with
 * READ UPDATE semantics (row lock) and rewritten with the {@code @Version} check.
 */
@Service
public class Cobil00cService {

  static final String TRANID = "CB00";
  static final String PGM = "COBIL00C";

  /** GET-CURRENT-TIMESTAMP: FORMATTIME YYYYMMDD DATESEP('-') + TIME TIMESEP(':') + zero micros. */
  static final DateTimeFormatter PAYMENT_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.000000");

  private final AccountFile accounts;
  private final XrefFile xref;
  private final TransactFile transact;
  private final BusinessClock clock;

  public Cobil00cService(
      AccountFile accounts, XrefFile xref, TransactFile transact, BusinessClock clock) {
    this.accounts = accounts;
    this.xref = xref;
    this.transact = transact;
    this.clock = clock;
  }

  @Transactional
  public ProgramResult<Cobil00Screen.Output> run(
      ConversationContext ctx, CommareaExtension ext, Action aid, Map<String, String> input) {
    MapBuffer map = new MapBuffer(ScreenId.COBIL00, Cobil00Screen.Output.class, clock, false);
    // COBIL00C MAIN-PARA
    if (ctx.programContext() != ProgramContext.REENTER) {
      ctx = ctx.reenter();
      map.lowValues();
      map.cursor("ACTIDIN");
      if (!CobolFmt.blank(ext.selected())) {
        map.set("ACTIDIN", ext.selected());
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
              MapBuffer.xctl(ScreenId.COBIL00, Navigation.screenOf(next.toProgram()), next),
              Cotrn02cService.withAid(ext, aid));
        }
        case PF4 -> clearCurrentScreen(map);
        default -> {
          map.err(true);
          map.message(MapBuffer.MSG_INVALID_KEY);
          map.send();
        }
      }
    }
    return new ProgramResult<>(
        map.response(Cobil00Screen.Output.class, ctx), Cotrn01cService.noAid(ext));
  }

  // COBIL00C PROCESS-ENTER-KEY
  private void processEnterKey(MapBuffer map) {
    boolean confPay = false;
    Account account = null;
    Long acctId = null;
    if (CobolFmt.blank(map.get("ACTIDIN"))) {
      map.error("Acct ID can NOT be empty...", "ACTIDIN");
      map.send();
    }
    if (!map.err()) {
      acctId = CobolFmt.zonedMove(CobolFmt.pad(map.get("ACTIDIN"), 11));
      String confirm = map.get("CONFIRM");
      switch (confirm) {
        case "Y", "y" -> {
          confPay = true;
          account = readAcctdatFile(map, acctId);
        }
        case "N", "n" -> {
          clearCurrentScreen(map);
          map.err(true);
        }
        default -> {
          if (CobolFmt.blank(confirm)) {
            account = readAcctdatFile(map, acctId);
          } else {
            map.error("Invalid value. Valid values are (Y/N)...", "CONFIRM");
            map.send();
          }
        }
      }
      if (account != null) {
        map.set("CURBAL", CobolFmt.editSigned(account.getAcctCurrBal(), 10));
      }
    }
    if (!map.err()
        && account.getAcctCurrBal().signum() <= 0
        && !CobolFmt.blank(map.get("ACTIDIN"))) {
      map.error("You have nothing to pay...", "ACTIDIN");
      map.send();
    }
    if (!map.err()) {
      if (confPay) {
        pay(map, account, acctId);
      } else {
        map.message("Confirm to make a bill payment...");
        map.cursor("CONFIRM");
      }
      map.send();
    }
  }

  private void pay(MapBuffer map, Account account, long acctId) {
    CardXref card = readCxacaixFile(map, acctId);
    String id = TranIdAllocator.next(transact);
    if (id == null) {
      map.error("Unable to lookup Transaction...", "ACTIDIN");
      map.send();
      rollback();
      return;
    }
    Transaction t = new Transaction();
    t.setTranId(id);
    t.setTranTypeCd("02");
    t.setTranCatCd(2);
    t.setTranSource(CobolFmt.pad("POS TERM", 10));
    t.setTranDesc(CobolFmt.pad("BILL PAYMENT - ONLINE", 100));
    t.setTranAmt(account.getAcctCurrBal());
    t.setTranCardNum(CobolFmt.pad(card == null ? "" : card.getXrefCardNum(), 16));
    t.setTranMerchantId(999999999);
    t.setTranMerchantName(CobolFmt.pad("BILL PAYMENT", 50));
    t.setTranMerchantCity(CobolFmt.pad("N/A", 50));
    t.setTranMerchantZip(CobolFmt.pad("N/A", 10));
    String ts = PAYMENT_TS.format(clock.now());
    t.setTranOrigTs(ts);
    t.setTranProcTs(ts);
    t.setFiller(CobolFmt.pad("", 20));
    // COBIL00C WRITE-TRANSACT-FILE
    switch (transact.write(t)) {
      case NORMAL -> {
        map.cursor("ACTIDIN");
        map.spaces("ACTIDIN", "CURBAL", "CONFIRM");
        map.message("");
        map.green();
        map.message("Payment successful. " + " Your Transaction ID is " + id.strip() + ".");
        map.send();
      }
      case DUPREC, DUPKEY -> {
        map.error("Tran ID already exist...", "ACTIDIN");
        map.send();
        rollback();
        return;
      }
      default -> {
        map.error("Unable to Add Bill pay Transaction...", "ACTIDIN");
        map.send();
        rollback();
        return;
      }
    }
    // COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT, then UPDATE-ACCTDAT-FILE
    BigDecimal bal =
        CobolMath.subtract(
            account.getAcctCurrBal(), t.getTranAmt(), 10, 2, CobolMath.Mode.TRUNCATE);
    account.setAcctCurrBal(bal);
    Resp r = accounts.rewrite(account);
    if (r == Resp.NOTFND) {
      map.error("Account ID NOT found...", "ACTIDIN");
      map.send();
    } else if (r != Resp.NORMAL) {
      map.error("Unable to Update Account...", "ACTIDIN");
      map.send();
    }
  }

  /** PE (proposed): a failed WRITE no longer leaves the balance zeroed by the REWRITE. */
  private static void rollback() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
  }

  // COBIL00C READ-ACCTDAT-FILE
  private Account readAcctdatFile(MapBuffer map, Long acctId) {
    ReadResult<Account> r =
        acctId == null ? ReadResult.of(Resp.NOTFND) : accounts.readForUpdate(acctId);
    switch (r.resp()) {
      case NORMAL -> {
        return r.record();
      }
      case NOTFND -> {
        map.error("Account ID NOT found...", "ACTIDIN");
        map.send();
      }
      default -> {
        map.error("Unable to lookup Account...", "ACTIDIN");
        map.send();
      }
    }
    return null;
  }

  // COBIL00C READ-CXACAIX-FILE (errors are reported but, as in COBOL, do not stop the payment)
  private CardXref readCxacaixFile(MapBuffer map, long acctId) {
    ReadResult<CardXref> r = xref.readByAcct(acctId);
    switch (r.resp()) {
      case NORMAL -> {
        return r.record();
      }
      case NOTFND -> {
        map.error("Account ID NOT found...", "ACTIDIN");
        map.send();
      }
      default -> {
        map.error("Unable to lookup XREF AIX file...", "ACTIDIN");
        map.send();
      }
    }
    return null;
  }

  // COBIL00C CLEAR-CURRENT-SCREEN
  private static void clearCurrentScreen(MapBuffer map) {
    map.cursor("ACTIDIN");
    map.spaces("ACTIDIN", "CURBAL", "CONFIRM");
    map.message("");
    map.send();
  }
}
