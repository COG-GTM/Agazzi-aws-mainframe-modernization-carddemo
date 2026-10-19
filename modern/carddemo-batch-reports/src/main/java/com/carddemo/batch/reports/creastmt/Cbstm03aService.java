package com.carddemo.batch.reports.creastmt;

import static com.carddemo.batch.reports.support.CobolText.delimitedBy;
import static com.carddemo.batch.reports.support.CobolText.stringInto;
import static com.carddemo.batch.reports.support.FixedRecords.pad;

import com.carddemo.batch.reports.support.CobolEdit;
import com.carddemo.batch.reports.support.JobAbendException;
import com.carddemo.batch.reports.support.Ksds;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.StatementCustomerRecord;
import com.carddemo.contracts.record.StatementTransactionRecord;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.copybook.ZonedRecordCodec;
import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CBSTM03A (+ CBSTM03B file I/O): one text statement (80 columns) and one HTML statement (100
 * columns) per card cross-reference record. Transactions (TRXFL KSDS, {@code COSTM01}) are first
 * loaded into the in-memory table of the program (51 cards x 10 transactions).
 */
public final class Cbstm03aService {

  public static final String PROGRAM = "CBSTM03A";
  public static final int STMT_LRECL = 80;
  public static final int HTML_LRECL = 100;
  static final int MAX_CARDS = 51;
  static final int MAX_TRANS_PER_CARD = 10;

  static final ZonedRecordCodec<StatementTransactionRecord> TRNX =
      ZonedRecordCodec.of(StatementTransactionRecord.class);
  static final ZonedRecordCodec<StatementCustomerRecord> CUST =
      ZonedRecordCodec.of(StatementCustomerRecord.class);

  private static final String TR_S = "<tr>";
  private static final String TR_E = "</tr>";
  private static final String TD_E = "</td>";
  private static final String TD3 = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:";
  private static final String L10 = TD3 + "#1d1d96b3;\">";
  private static final String L15 = TD3 + "#FFAF33;\">";
  private static final String L22_35 = TD3 + "#f2f2f2;\">";
  private static final String L30_42 = TD3 + "#33FFD1; text-align:center;\">";
  private static final String P16 = "<p style=\"font-size:16px\">";

  /** Output of one run: STMTFILE and HTMLFILE records. */
  public record Statements(List<String> text, List<String> html) {}

  /** Files read through CBSTM03B. */
  public record Files(List<String> trnxFile, List<String> xrefFile, Ksds custFile, Ksds acctFile) {}

  private record Card(String cardNum, List<String> trans) {}

  private final List<String> text = new ArrayList<>();
  private final List<String> html = new ArrayList<>();

  private Cbstm03aService() {}

  public static Statements run(Files files) {
    Cbstm03aService s = new Cbstm03aService();
    List<Card> table = loadTransactions(files.trnxFile());
    for (String xrefRaw : files.xrefFile()) {
      String xref = pad(xrefRaw, 50);
      String cust = read(files.custFile(), xref.substring(16, 25), "CUSTFILE");
      String acct = read(files.acctFile(), xref.substring(25, 36), "ACCTFILE");
      s.createStatement(CUST.decodeLine(cust), CoreCodecs.ACCOUNT.decodeLine(acct));
      s.writeTransactions(table, xref.substring(0, 16));
    }
    return new Statements(s.text, s.html);
  }

  /** 8100-TRNXFILE-OPEN + 8500-READTRNX-READ. */
  static List<Card> loadTransactions(List<String> trnxFile) {
    if (trnxFile.isEmpty()) {
      throw new JobAbendException(PROGRAM, "ERROR READING TRNXFILE RETURN CODE: 10");
    }
    List<Card> table = new ArrayList<>();
    for (String raw : trnxFile) {
      String r = pad(raw, 350);
      String card = r.substring(0, 16);
      if (table.isEmpty() || !table.get(table.size() - 1).cardNum().equals(card)) {
        if (table.size() == MAX_CARDS) {
          throw new JobAbendException(PROGRAM, "WS-CARD-TBL overflow (> 51 cards)");
        }
        table.add(new Card(card, new ArrayList<>()));
      }
      List<String> trans = table.get(table.size() - 1).trans();
      if (trans.size() == MAX_TRANS_PER_CARD) {
        throw new JobAbendException(PROGRAM, "WS-TRAN-TBL overflow for card " + card);
      }
      trans.add(r);
    }
    return table;
  }

  private static String read(Ksds file, String key, String dd) {
    String rec = file.read(key);
    if (rec == null) {
      throw new JobAbendException(PROGRAM, "ERROR READING " + dd + " RETURN CODE: 23 key " + key);
    }
    return rec;
  }

  /** 5000-CREATE-STATEMENT with 5100-WRITE-HTML-HEADER and 5200-WRITE-HTML-NMADBS. */
  private void createStatement(StatementCustomerRecord cust, AccountRecord acct) {
    text("*".repeat(31) + "START OF STATEMENT" + "*".repeat(31));
    String acctId = pad(String.format("%011d", acct.acctId()), 20);
    String name =
        stringInto(
            75,
            delimitedBy(cust.custFirstName(), " "),
            " ",
            delimitedBy(cust.custMiddleName(), " "),
            " ",
            delimitedBy(cust.custLastName(), " "),
            " ");
    String add1 = pad(cust.custAddrLine1(), 50);
    String add2 = pad(cust.custAddrLine2(), 50);
    String add3 =
        stringInto(
            80,
            delimitedBy(cust.custAddrLine3(), " "),
            " ",
            delimitedBy(cust.custAddrStateCd(), " "),
            " ",
            delimitedBy(cust.custAddrCountryCd(), " "),
            " ",
            delimitedBy(cust.custAddrZip(), " "),
            " ");
    String currBal = CobolEdit.zeroFilledTrailingSign(acct.acctCurrBal());
    String fico = pad(String.format("%03d", cust.custFicoCreditScore() % 1000), 20);

    html(
        "<!DOCTYPE html>",
        "<html lang=\"en\">",
        "<head>",
        "<meta charset=\"utf-8\">",
        "<title>HTML Table Layout</title>",
        "</head>",
        "<body style=\"margin:0px;\">",
        "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">",
        TR_S,
        L10,
        "<h3>Statement for Account Number: " + acctId + "</h3>",
        TD_E,
        TR_E,
        TR_S,
        L15,
        P16 + "Bank of XYZ</p>",
        "<p>410 Terry Ave N</p>",
        "<p>Seattle WA 99999</p>",
        TD_E,
        TR_E,
        TR_S,
        L22_35);
    html(
        P16 + delimitedBy(pad(name, 50), "  ") + "  </p>",
        "<p>" + delimitedBy(add1, "  ") + "  </p>",
        "<p>" + delimitedBy(add2, "  ") + "  </p>",
        "<p>" + delimitedBy(add3, "  ") + "  </p>",
        TD_E,
        TR_E,
        TR_S,
        L30_42,
        P16 + "Basic Details</p>",
        TD_E,
        TR_E,
        TR_S,
        L22_35,
        "<p>Account ID         : " + acctId + "</p>",
        "<p>Current Balance    : " + currBal + "</p>",
        "<p>FICO Score         : " + fico + "</p>",
        TD_E,
        TR_E,
        TR_S,
        L30_42,
        P16 + "Transaction Summary</p>",
        TD_E,
        TR_E,
        TR_S,
        td("25%", "#33FF5E", "left"),
        P16 + "Tran ID</p>",
        TD_E,
        td("55%", "#33FF5E", "left"),
        P16 + "Tran Details</p>",
        TD_E,
        td("20%", "#33FF5E", "right"),
        P16 + "Amount</p>",
        TD_E,
        TR_E);

    String dashes = "-".repeat(80);
    text(name + "     ");
    text(add1);
    text(add2);
    text(add3);
    text(dashes);
    text(" ".repeat(33) + pad("Basic Details", 14) + " ".repeat(33));
    text(dashes);
    text("Account ID         :" + acctId);
    text("Current Balance    :" + currBal);
    text("FICO Score         :" + fico);
    text(dashes);
    text(" ".repeat(30) + "TRANSACTION SUMMARY " + " ".repeat(30));
    text(dashes);
    text(pad("Tran ID         ", 16) + pad("Tran Details    ", 51) + "  Tran Amount");
    text(dashes);
  }

  /** 4000-TRNXFILE-GET with 6000-WRITE-TRANS. */
  private void writeTransactions(List<Card> table, String xrefCard) {
    BigDecimal total = BigDecimal.ZERO.setScale(2);
    for (Card c : table) {
      if (c.cardNum().compareTo(xrefCard) > 0) {
        break;
      }
      if (c.cardNum().equals(xrefCard)) {
        for (String raw : c.trans()) {
          StatementTransactionRecord t = decode(raw);
          String tranDt = pad(t.trnxDesc(), 49);
          String amt = CobolEdit.zeroSuppressedTrailingSign(t.trnxAmt());
          text(t.trnxId() + " " + tranDt + "$" + amt);
          html(
              TR_S,
              td("25%", "#f2f2f2", "left"),
              "<p>" + t.trnxId() + "</p>",
              TD_E,
              td("55%", "#f2f2f2", "left"),
              "<p>" + tranDt + "</p>",
              TD_E,
              td("20%", "#f2f2f2", "right"),
              "<p>" + amt + "</p>",
              TD_E,
              TR_E);
          total = CobolMath.add(total, t.trnxAmt(), 9, 2, CobolMath.Mode.TRUNCATE);
        }
      }
    }
    text("-".repeat(80));
    text("Total EXP:" + " ".repeat(56) + "$" + CobolEdit.zeroSuppressedTrailingSign(total));
    text("*".repeat(32) + "END OF STATEMENT" + "*".repeat(32));
    html(TR_S, L10, "<h3>End of Statement</h3>", TD_E, TR_E, "</table>", "</body>", "</html>");
  }

  private static StatementTransactionRecord decode(String raw) {
    try {
      return TRNX.decodeLine(raw);
    } catch (RuntimeException e) {
      throw new JobAbendException(
          PROGRAM, "malformed TRXFL record " + raw.substring(0, 32).trim(), e);
    }
  }

  private static String td(String width, String color, String align) {
    return "<td style=\"width:"
        + width
        + "; padding:0px 5px; background-color:"
        + color
        + "; text-align:"
        + align
        + ";\">";
  }

  private void text(String line) {
    text.add(pad(line, STMT_LRECL));
  }

  private void html(String... lines) {
    for (String l : lines) {
      html.add(pad(l, HTML_LRECL));
    }
  }
}
