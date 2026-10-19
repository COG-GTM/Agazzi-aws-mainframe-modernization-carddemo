package com.carddemo.batch.posting.cbtrn01c;

import com.carddemo.batch.posting.dataset.Keys;
import com.carddemo.batch.posting.dataset.PostingDatasets;
import com.carddemo.batch.posting.support.Sysout;
import com.carddemo.contracts.record.CardXrefRecord;
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
    for (String raw : daily) {
      last = raw;
      sysout.display(raw);
      verify(ds, raw, sysout);
    }
    verify(ds, last, sysout);
    sysout.display("END OF EXECUTION OF PROGRAM " + PROGRAM);
    return 0;
  }

  /** Only DALYTRAN-ID (1:16) and DALYTRAN-CARD-NUM (263:16) are used: no numeric decoding. */
  private void verify(PostingDatasets ds, String raw, Sysout sysout) {
    String rec = Keys.pic(raw, 350);
    String card = rec.substring(262, 278);
    Optional<CardXrefRecord> xref = ds.cardXref().read(card);
    if (xref.isEmpty()) {
      sysout.display("INVALID CARD NUMBER FOR XREF");
      sysout.display(
          "CARD NUMBER "
              + card
              + " COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-"
              + rec.substring(0, 16));
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
