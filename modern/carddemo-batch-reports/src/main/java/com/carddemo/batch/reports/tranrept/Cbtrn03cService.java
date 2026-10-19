package com.carddemo.batch.reports.tranrept;

import static com.carddemo.batch.reports.support.FixedRecords.pad;

import com.carddemo.batch.reports.support.CobolEdit;
import com.carddemo.batch.reports.support.JobAbendException;
import com.carddemo.batch.reports.support.Ksds;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CBTRN03C: prints the daily transaction report (133 columns, {@code CVTRA07Y}) from the sorted
 * TRANSACT.DALY file. Paragraph structure and quirks are preserved: the {@code NEXT SENTENCE} on a
 * record outside the date range ends the main loop (no totals), at end of file the last {@code
 * TRAN-AMT} is added once more to the page/account totals before the final page and grand totals,
 * and the account total of the last card is never printed.
 */
public final class Cbtrn03cService {

  public static final String PROGRAM = "CBTRN03C";
  public static final int REPORT_LRECL = 133;
  static final int TRAN_LRECL = 350;
  private static final int PAGE_SIZE = 20;
  private static final String HEADER_2 = "-".repeat(REPORT_LRECL);
  private static final String HEADER_1 =
      pad("Transaction ID", 17)
          + pad("Account ID", 12)
          + pad("Transaction Type", 19)
          + pad("Tran Category", 35)
          + pad("Tran Source", 14)
          + " "
          + pad("        Amount", 16);

  /** Date range read from the DATEPARM dataset ({@code WS-START-DATE X(10), X, WS-END-DATE}). */
  public record DateParm(String startDate, String endDate) {
    public static DateParm parse(String record) {
      String r = pad(record, 21);
      return new DateParm(r.substring(0, 10), r.substring(11, 21));
    }

    public String toRecord() {
      return pad(startDate, 10) + " " + pad(endDate, 10);
    }
  }

  /** Reference files read by key ({@code CARDXREF}, {@code TRANTYPE}, {@code TRANCATG}). */
  public record Lookups(Ksds cardXref, Ksds tranType, Ksds tranCatg) {}

  private final Lookups lookups;
  private final List<String> report = new ArrayList<>();
  private String reportStart = "";
  private String reportEnd = "";
  private boolean firstTime = true;
  private long lineCounter;
  private BigDecimal pageTotal = BigDecimal.ZERO.setScale(2);
  private BigDecimal accountTotal = BigDecimal.ZERO.setScale(2);
  private BigDecimal grandTotal = BigDecimal.ZERO.setScale(2);
  private String currCardNum = " ".repeat(16);
  private String xrefRecord = " ".repeat(50);

  private Cbtrn03cService(Lookups lookups) {
    this.lookups = lookups;
  }

  /**
   * Runs the program. {@code dateParm} is the first DATEPARM record or {@code null} when the
   * dataset is empty (status 10: the program ends without writing anything).
   */
  public static List<String> run(List<String> tranFile, Lookups lookups, String dateParm) {
    Cbtrn03cService s = new Cbtrn03cService(lookups);
    s.mainline(tranFile, dateParm);
    return s.report;
  }

  private void mainline(List<String> tranFile, String dateParm) {
    if (dateParm == null) {
      return;
    }
    DateParm range = DateParm.parse(dateParm);
    String tran = " ".repeat(TRAN_LRECL);
    int next = 0;
    while (true) {
      boolean eof = next >= tranFile.size();
      if (!eof) {
        tran = pad(tranFile.get(next++), TRAN_LRECL);
      }
      String procDate = tran.substring(304, 314);
      if (procDate.compareTo(range.startDate()) < 0 || procDate.compareTo(range.endDate()) > 0) {
        return;
      }
      if (eof) {
        BigDecimal amt = amount(tran);
        pageTotal = add(pageTotal, amt);
        accountTotal = add(accountTotal, amt);
        writePageTotals();
        writeGrandTotals();
        return;
      }
      String card = tran.substring(262, 278);
      if (!currCardNum.equals(card)) {
        if (!firstTime) {
          writeAccountTotals();
        }
        currCardNum = card;
        xrefRecord = lookup(lookups.cardXref(), card, "INVALID CARD NUMBER : ");
      }
      String typeCd = tran.substring(16, 18);
      String typeRec = lookup(lookups.tranType(), typeCd, "INVALID TRANSACTION TYPE : ");
      String catKey = typeCd + tran.substring(18, 22);
      String catRec = lookup(lookups.tranCatg(), catKey, "INVALID TRAN CATG KEY : ");
      writeTransactionReport(range, tran, typeRec, catRec);
    }
  }

  private static String lookup(Ksds file, String key, String message) {
    String rec = file.read(key);
    if (rec == null) {
      throw new JobAbendException(PROGRAM, message + key + " (status 23)");
    }
    return rec;
  }

  private void writeTransactionReport(DateParm range, String tran, String type, String cat) {
    if (firstTime) {
      firstTime = false;
      reportStart = range.startDate();
      reportEnd = range.endDate();
      writeHeaders();
    }
    if (lineCounter % PAGE_SIZE == 0) {
      writePageTotals();
      writeHeaders();
    }
    BigDecimal amt = amount(tran);
    pageTotal = add(pageTotal, amt);
    accountTotal = add(accountTotal, amt);
    writeDetail(tran, type, cat, amt);
  }

  private void writePageTotals() {
    write(pad("Page Total", 11) + ".".repeat(86) + CobolEdit.plusZzz(pageTotal));
    grandTotal = add(grandTotal, pageTotal);
    pageTotal = BigDecimal.ZERO.setScale(2);
    lineCounter++;
    write(HEADER_2);
    lineCounter++;
  }

  private void writeAccountTotals() {
    write(pad("Account Total", 13) + ".".repeat(84) + CobolEdit.plusZzz(accountTotal));
    accountTotal = BigDecimal.ZERO.setScale(2);
    lineCounter++;
    write(HEADER_2);
    lineCounter++;
  }

  private void writeGrandTotals() {
    write(pad("Grand Total", 11) + ".".repeat(86) + CobolEdit.plusZzz(grandTotal));
  }

  private void writeHeaders() {
    write(
        pad("DALYREPT", 38)
            + pad("Daily Transaction Report", 41)
            + "Date Range: "
            + pad(reportStart, 10)
            + " to "
            + pad(reportEnd, 10));
    lineCounter++;
    write("");
    lineCounter++;
    write(HEADER_1);
    lineCounter++;
    write(HEADER_2);
    lineCounter++;
  }

  private void writeDetail(String tran, String type, String cat, BigDecimal amt) {
    write(
        tran.substring(0, 16)
            + " "
            + xrefRecord.substring(25, 36)
            + " "
            + tran.substring(16, 18)
            + "-"
            + type.substring(2, 17)
            + " "
            + tran.substring(18, 22)
            + "-"
            + cat.substring(6, 35)
            + " "
            + tran.substring(22, 32)
            + "    "
            + CobolEdit.minusZzz(amt)
            + "  ");
    lineCounter++;
  }

  private void write(String line) {
    report.add(pad(line, REPORT_LRECL));
  }

  private static BigDecimal add(BigDecimal total, BigDecimal amt) {
    return CobolMath.add(total, amt, 9, 2, CobolMath.Mode.TRUNCATE);
  }

  static BigDecimal amount(String tran) {
    if (tran.isBlank()) {
      return BigDecimal.ZERO.setScale(2);
    }
    try {
      return CoreCodecs.TRANSACTION.decodeLine(tran).tranAmt();
    } catch (RuntimeException e) {
      throw new JobAbendException(
          PROGRAM, "malformed TRAN-AMT in record " + tran.substring(0, 16), e);
    }
  }
}
