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
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** CBIMPORT (CBIMPORT STEP01): splits EXPFILE into the normalized files plus an error file. */
public final class CbImport {

  public static final String NAME = "CBIMPORT";
  public static final int ERROR_LENGTH = 132;

  /** Output files; the legacy JCL has no CARDOUT DD, so {@code cards} may be null (DD DUMMY). */
  public record Outputs(
      Supplier<RecordSink> customers,
      Supplier<RecordSink> accounts,
      Supplier<RecordSink> xrefs,
      Supplier<RecordSink> transactions,
      Supplier<RecordSink> cards,
      Supplier<RecordSink> errors) {}

  /** Import counters as displayed by 4000-FINALIZE. */
  public record Counts(
      long read,
      long customers,
      long accounts,
      long xrefs,
      long transactions,
      long cards,
      long errors,
      long unknown) {}

  private static final String[] OUTPUT_NAMES = {
    "CUSTOMER-OUTPUT",
    "ACCOUNT-OUTPUT",
    "XREF-OUTPUT",
    "TRANSACTION-OUTPUT",
    "CARD-OUTPUT",
    "ERROR-OUTPUT"
  };
  private static final String[] WRITE_NAMES = {
    "customer", "account", "xref", "transaction", "card"
  };

  private final BusinessClock clock;

  public CbImport(BusinessClock clock) {
    this.clock = clock;
  }

  public Counts run(Supplier<RecordSource> input, Outputs outputs, Sysout sysout) {
    sysout.display("CBIMPORT: Starting Customer Data Import");
    LocalDateTime now = clock.now();
    RecordSource in = open(input, "EXPORT-INPUT", sysout);
    Supplier<RecordSink> dummy = () -> NullSink.INSTANCE;
    Supplier<RecordSink>[] suppliers =
        array(
            outputs.customers(),
            outputs.accounts(),
            outputs.xrefs(),
            outputs.transactions(),
            outputs.cards() == null ? dummy : outputs.cards(),
            outputs.errors());
    RecordSink[] sinks = new RecordSink[6];
    for (int i = 0; i < 6; i++) {
      sinks[i] = open(suppliers[i], OUTPUT_NAMES[i], sysout);
    }
    sysout.display("CBIMPORT: Import Date: " + now.format(DateTimeFormatter.ISO_LOCAL_DATE));
    sysout.display("CBIMPORT: Import Time: " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    // INITIALIZE leaves the FD FILLER untouched: each output keeps its own record buffer.
    Map<ExportLayout, byte[]> buffers = new EnumMap<>(ExportLayout.class);
    for (ExportLayout l : ExportLayout.values()) {
      buffers.put(l, new byte[l.legacyLength()]);
    }
    long[] counts = new long[5];
    long read = 0;
    long errors = 0;
    long unknown = 0;
    while (true) {
      Optional<byte[]> next;
      try {
        next = in.next();
      } catch (FileStatusException e) {
        throw abend(sysout, "ERROR: Reading EXPORT-INPUT, Status: " + e.status(), e);
      }
      if (next.isEmpty()) {
        break;
      }
      read++;
      byte[] export = next.get();
      Optional<ExportLayout> layout = ExportLayout.of(export[0]);
      if (layout.isPresent()) {
        int idx = layout.get().ordinal();
        byte[] legacy = buffers.get(layout.get());
        layout.get().restore(export, legacy);
        try {
          sinks[idx].write(legacy.clone());
        } catch (FileStatusException e) {
          throw abend(
              sysout, "ERROR: Writing " + WRITE_NAMES[idx] + " record, Status: " + e.status(), e);
        }
        counts[idx]++;
      } else {
        unknown++;
        try {
          sinks[5].write(errorRecord(now, export));
        } catch (FileStatusException e) {
          sysout.display("ERROR: Writing error record, Status: " + e.status());
        }
        errors++;
      }
    }
    sysout.display("CBIMPORT: Import validation completed");
    sysout.display("CBIMPORT: No validation errors detected");
    try {
      in.close();
      for (RecordSink s : sinks) {
        s.close();
      }
    } catch (FileStatusException e) {
      throw new AbendException(NAME, 0, e);
    }
    Counts c =
        new Counts(read, counts[0], counts[1], counts[2], counts[3], counts[4], errors, unknown);
    sysout.display("CBIMPORT: Import completed");
    sysout.display("CBIMPORT: Total Records Read: " + CbExport.nine(c.read()));
    sysout.display("CBIMPORT: Customers Imported: " + CbExport.nine(c.customers()));
    sysout.display("CBIMPORT: Accounts Imported: " + CbExport.nine(c.accounts()));
    sysout.display("CBIMPORT: XRefs Imported: " + CbExport.nine(c.xrefs()));
    sysout.display("CBIMPORT: Transactions Imported: " + CbExport.nine(c.transactions()));
    sysout.display("CBIMPORT: Cards Imported: " + CbExport.nine(c.cards()));
    sysout.display("CBIMPORT: Errors Written: " + CbExport.nine(c.errors()));
    sysout.display("CBIMPORT: Unknown Record Types: " + CbExport.nine(c.unknown()));
    return c;
  }

  /** WS-ERROR-RECORD: CURRENT-DATE | type | sequence 9(7) | message, padded to 132. */
  static byte[] errorRecord(LocalDateTime now, byte[] export) {
    byte[] r = CobolData.spaces(ERROR_LENGTH);
    String ts = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "00+0000";
    CobolData.putText(r, 0, 26, ts);
    r[26] = '|';
    r[27] = export[0];
    r[28] = '|';
    BigDecimal seq = CobolData.binary(export, ExportLayout.SEQUENCE_OFFSET, 4, 0, false);
    CobolData.putZoned(r, 29, seq, 7, 0, false);
    r[36] = '|';
    CobolData.putText(r, 37, 50, "Unknown record type encountered");
    return r;
  }

  @SafeVarargs
  private static Supplier<RecordSink>[] array(Supplier<RecordSink>... s) {
    return s;
  }

  private static <T> T open(Supplier<T> supplier, String file, Sysout sysout) {
    try {
      return supplier.get();
    } catch (UncheckedIOException e) {
      String status =
          e.getCause() instanceof FileStatusException fse
              ? fse.status()
              : FileStatusException.NOT_FOUND;
      throw abend(
          sysout,
          "ERROR: Cannot open " + file + ", Status: " + status,
          new FileStatusException(status, e.getMessage(), e));
    }
  }

  private static AbendException abend(Sysout sysout, String message, FileStatusException e) {
    sysout.display(message);
    sysout.display(NAME + ": ABENDING PROGRAM");
    sysout.display("CEE3ABD ABEND U0");
    return new AbendException(NAME, 0, e);
  }

  /** DD DUMMY. */
  enum NullSink implements RecordSink {
    INSTANCE;

    @Override
    public void write(byte[] record) {}

    @Override
    public void close() {}
  }
}
