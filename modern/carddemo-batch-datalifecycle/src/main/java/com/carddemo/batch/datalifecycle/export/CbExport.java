package com.carddemo.batch.datalifecycle.export;

import com.carddemo.batch.datalifecycle.cobol.CobolData;
import com.carddemo.batch.datalifecycle.program.AbendException;
import com.carddemo.batch.datalifecycle.program.FileStatusException;
import com.carddemo.batch.datalifecycle.program.RecordSink;
import com.carddemo.batch.datalifecycle.program.RecordSource;
import com.carddemo.batch.datalifecycle.program.Sysout;
import com.carddemo.contracts.port.BusinessClock;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** CBEXPORT (CBEXPORT STEP02): customer, account, xref, transaction and card files to EXPFILE. */
public final class CbExport {

  public static final String NAME = "CBEXPORT";

  /** Inputs in program order; each opens lazily so open failures map to a file status. */
  public record Inputs(
      Supplier<RecordSource> customers,
      Supplier<RecordSource> accounts,
      Supplier<RecordSource> xrefs,
      Supplier<RecordSource> transactions,
      Supplier<RecordSource> cards) {}

  /** Per-type and total export counts. */
  public record Counts(long customers, long accounts, long xrefs, long transactions, long cards) {
    public long total() {
      return customers + accounts + xrefs + transactions + cards;
    }
  }

  private static final String[][] PHASES = {
    {"CUSTOMER-INPUT", "customer records", "Customers exported: "},
    {"ACCOUNT-INPUT", "account records", "Accounts exported: "},
    {"XREF-INPUT", "cross-reference records", "Cross-references exported: "},
    {"TRANSACTION-INPUT", "transaction records", "Transactions exported: "},
    {"CARD-INPUT", "card records", "Cards exported: "}
  };
  private static final ExportLayout[] LAYOUTS = {
    ExportLayout.CUSTOMER,
    ExportLayout.ACCOUNT,
    ExportLayout.XREF,
    ExportLayout.TRANSACTION,
    ExportLayout.CARD
  };

  private final BusinessClock clock;

  public CbExport(BusinessClock clock) {
    this.clock = clock;
  }

  public Counts run(Inputs inputs, Supplier<RecordSink> output, Sysout sysout) {
    sysout.display("CBEXPORT: Starting Customer Data Export");
    LocalDateTime now = clock.now();
    String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    String timestamp = date + " " + time + ".00";
    List<Supplier<RecordSource>> suppliers =
        List.of(
            inputs.customers(),
            inputs.accounts(),
            inputs.xrefs(),
            inputs.transactions(),
            inputs.cards());
    List<RecordSource> sources = new ArrayList<>();
    for (int i = 0; i < suppliers.size(); i++) {
      sources.add(open(suppliers.get(i), "ERROR: Cannot open " + PHASES[i][0], sysout));
    }
    RecordSink sink = open(output, "ERROR: Cannot open EXPORT-OUTPUT", sysout);
    sysout.display("CBEXPORT: Export Date: " + date);
    sysout.display("CBEXPORT: Export Time: " + time);
    long[] counts = new long[5];
    int sequence = 0;
    for (int i = 0; i < 5; i++) {
      sysout.display("CBEXPORT: Processing " + PHASES[i][1]);
      while (true) {
        Optional<byte[]> rec;
        try {
          rec = sources.get(i).next();
        } catch (FileStatusException e) {
          throw abend(sysout, "ERROR: Reading " + PHASES[i][0], e);
        }
        if (rec.isEmpty()) {
          break;
        }
        sequence++;
        byte[] export = header(LAYOUTS[i].type(), timestamp, sequence);
        LAYOUTS[i].export(rec.get(), export);
        try {
          sink.write(export);
        } catch (FileStatusException e) {
          throw abend(sysout, "ERROR: Writing export record", e);
        }
        counts[i]++;
      }
      sysout.display("CBEXPORT: " + PHASES[i][2] + nine(counts[i]));
    }
    try {
      for (RecordSource s : sources) {
        s.close();
      }
      sink.close();
    } catch (FileStatusException e) {
      throw new AbendException(NAME, 0, e);
    }
    Counts result = new Counts(counts[0], counts[1], counts[2], counts[3], counts[4]);
    sysout.display("CBEXPORT: Export completed");
    sysout.display("CBEXPORT: Customers Exported: " + nine(result.customers()));
    sysout.display("CBEXPORT: Accounts Exported: " + nine(result.accounts()));
    sysout.display("CBEXPORT: XRefs Exported: " + nine(result.xrefs()));
    sysout.display("CBEXPORT: Transactions Exported: " + nine(result.transactions()));
    sysout.display("CBEXPORT: Cards Exported: " + nine(result.cards()));
    sysout.display("CBEXPORT: Total Records Exported: " + nine(result.total()));
    return result;
  }

  static byte[] header(char type, String timestamp, int sequence) {
    byte[] r = CobolData.spaces(ExportLayout.RECORD_LENGTH);
    r[0] = (byte) type;
    CobolData.putText(r, 1, 26, timestamp);
    CobolData.putBinary(r, ExportLayout.SEQUENCE_OFFSET, 4, BigDecimal.valueOf(sequence), 0);
    CobolData.putText(r, 31, 4, "0001");
    CobolData.putText(r, 35, 5, "NORTH");
    return r;
  }

  static String nine(long n) {
    return "%09d".formatted(n % 1_000_000_000L);
  }

  private static <T> T open(Supplier<T> supplier, String message, Sysout sysout) {
    try {
      return supplier.get();
    } catch (UncheckedIOException e) {
      String status =
          e.getCause() instanceof FileStatusException fse
              ? fse.status()
              : FileStatusException.NOT_FOUND;
      throw abend(sysout, message, new FileStatusException(status, e.getMessage(), e));
    }
  }

  static AbendException abend(Sysout sysout, String message, FileStatusException e) {
    sysout.display(message + ", Status: " + e.status());
    sysout.display(NAME + ": ABENDING PROGRAM");
    sysout.display("CEE3ABD ABEND U0");
    return new AbendException(NAME, 0, e);
  }
}
