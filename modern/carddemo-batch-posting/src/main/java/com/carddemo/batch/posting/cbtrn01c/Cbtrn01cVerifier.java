package com.carddemo.batch.posting.cbtrn01c;

import com.carddemo.batch.posting.dataset.Keys;
import com.carddemo.batch.posting.dataset.PostingDatasets;
import com.carddemo.batch.posting.support.Sysout;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * CBTRN01C: reads DALYTRAN and verifies card (XREF) and account of each transaction, reporting on
 * SYSOUT only. As in the COBOL main loop, the lookup is also performed once more on the end-of-file
 * iteration with the last record read (spaces when DALYTRAN is empty).
 */
public final class Cbtrn01cVerifier {

  public static final String PROGRAM = "CBTRN01C";

  public int run(PostingDatasets ds, Sysout sysout) {
    sysout.display("START OF EXECUTION OF PROGRAM " + PROGRAM);
    List<String> daily = ds.dailyTransactions();
    ds.customers().open();
    ds.cardXref().open();
    ds.cards().open();
    ds.accounts().open();
    ds.transactions().open();
    String last = " ".repeat(350);
    int n = 0;
    for (String raw : daily) {
      last = raw;
      sysout.display(raw);
      verify(ds, ds.decodeDailyTransaction(raw, ++n), sysout);
    }
    verify(ds, ds.decodeDailyTransaction(last, n + 1), sysout);
    sysout.display("END OF EXECUTION OF PROGRAM " + PROGRAM);
    return 0;
  }

  private void verify(PostingDatasets ds, DailyTransactionRecord tran, Sysout sysout) {
    String card = Keys.pic(tran.dalytranCardNum(), 16);
    Optional<CardXrefRecord> xref = ds.cardXref().read(card);
    if (xref.isEmpty()) {
      sysout.display("INVALID CARD NUMBER FOR XREF");
      sysout.display(
          "CARD NUMBER "
              + card
              + " COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-"
              + Keys.pic(tran.dalytranId(), 16));
      return;
    }
    CardXrefRecord x = xref.get();
    sysout.display("SUCCESSFUL READ OF XREF");
    sysout.display("CARD NUMBER: " + Keys.pic(x.xrefCardNum(), 16));
    sysout.display("ACCOUNT ID : " + Keys.account(x.xrefAcctId()));
    sysout.display("CUSTOMER ID: " + String.format(Locale.ROOT, "%09d", x.xrefCustId()));
    if (ds.accounts().read(Keys.account(x.xrefAcctId())).isPresent()) {
      sysout.display("SUCCESSFUL READ OF ACCOUNT FILE");
    } else {
      sysout.display("INVALID ACCOUNT NUMBER FOUND");
      sysout.display("ACCOUNT " + Keys.account(x.xrefAcctId()) + " NOT FOUND");
    }
  }
}
