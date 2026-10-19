package com.carddemo.batch.datalifecycle.program;

import static com.carddemo.batch.datalifecycle.cobol.CobolData.copy;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.displaySigned;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.putPacked;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.putZoned;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.text;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.zoned;

import com.carddemo.batch.datalifecycle.cobol.CobolData;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * CBACT01C (READACCT STEP05): reads ACCTFILE and writes OUTFILE (PSCOMP, 107 bytes), ARRYFILE
 * (ARRYPS, 110 bytes) and VBRCFILE (VBPS, variable records of 12 and 39 bytes).
 */
public final class Cbact01c {

  public static final String NAME = "CBACT01C";
  public static final int OUT_LENGTH = 107;
  public static final int ARRAY_LENGTH = 110;

  private static final BigDecimal DEFAULT_CYC_DEBIT = new BigDecimal("2525.00");
  private static final BigDecimal[] ARRAY_DEBITS = {
    new BigDecimal("1005.00"), new BigDecimal("1525.00"), new BigDecimal("-2500.00")
  };
  private static final BigDecimal ARRAY_BAL_3 = new BigDecimal("-1025.00");

  /** Output files of the step. */
  public record Outputs(RecordSink outFile, RecordSink arrayFile, RecordSink vbFile) {}

  /** Runs the program; returns the number of accounts processed or throws AbendException. */
  public long run(Supplier<RecordSource> acctFile, Supplier<Outputs> outputs, Sysout sysout) {
    sysout.display("START OF EXECUTION OF PROGRAM " + NAME);
    RecordSource in;
    try {
      in = Opener.open(acctFile);
    } catch (FileStatusException e) {
      throw LegacyIo.abend(sysout, NAME, "ERROR OPENING ACCTFILE", e);
    }
    Outputs out;
    try {
      out = Opener.open(outputs);
    } catch (FileStatusException e) {
      throw LegacyIo.abend(sysout, NAME, "ERROR OPENING OUTFILE" + e.status(), e);
    }
    // OUT-ACCT-REC lives in the FD buffer: the cycle debit is only moved when the input is zero.
    byte[] outRec = new byte[OUT_LENGTH];
    long count = 0;
    while (true) {
      Optional<byte[]> next;
      try {
        next = in.next();
      } catch (FileStatusException e) {
        throw LegacyIo.abend(sysout, NAME, "ERROR READING ACCOUNT FILE", e);
      }
      if (next.isEmpty()) {
        break;
      }
      byte[] acct = next.get();
      displayAccount(acct, sysout);
      try {
        out.outFile().write(populateOut(acct, outRec));
        out.arrayFile().write(populateArray(acct));
        byte[][] vb = populateVb(acct);
        sysout.display("VBRC-REC1:", vb[0]);
        sysout.display("VBRC-REC2:", vb[1]);
        out.vbFile().write(vb[0]);
        out.vbFile().write(vb[1]);
      } catch (FileStatusException e) {
        sysout.display("ACCOUNT FILE WRITE STATUS IS:" + e.status());
        throw LegacyIo.abend(sysout, NAME, "", e);
      }
      sysout.display(signNormalized(acct));
      count++;
    }
    try {
      in.close();
    } catch (FileStatusException e) {
      throw LegacyIo.abend(sysout, NAME, "ERROR CLOSING ACCOUNT FILE", e);
    }
    sysout.display("END OF EXECUTION OF PROGRAM " + NAME);
    try {
      out.outFile().close();
      out.arrayFile().close();
      out.vbFile().close();
    } catch (FileStatusException e) {
      throw new AbendException(NAME, 999, e);
    }
    return count;
  }

  private static void displayAccount(byte[] a, Sysout s) {
    s.display(label("ACCT-ID") + text(a, 0, 11));
    s.display(label("ACCT-ACTIVE-STATUS") + text(a, 11, 1));
    s.display(label("ACCT-CURR-BAL") + displaySigned(zoned(a, 12, 12, 2), 12, 2));
    s.display(label("ACCT-CREDIT-LIMIT") + displaySigned(zoned(a, 24, 12, 2), 12, 2));
    s.display(label("ACCT-CASH-CREDIT-LIMIT") + displaySigned(zoned(a, 36, 12, 2), 12, 2));
    s.display(label("ACCT-OPEN-DATE") + text(a, 48, 10));
    s.display(label("ACCT-EXPIRAION-DATE") + text(a, 58, 10));
    s.display(label("ACCT-REISSUE-DATE") + text(a, 68, 10));
    s.display(label("ACCT-CURR-CYC-CREDIT") + displaySigned(zoned(a, 78, 12, 2), 12, 2));
    s.display(label("ACCT-CURR-CYC-DEBIT") + displaySigned(zoned(a, 90, 12, 2), 12, 2));
    s.display(label("ACCT-GROUP-ID") + text(a, 112, 10));
    s.display("-------------------------------------------------");
  }

  /**
   * DISPLAY of a signed zoned item re-punches its sign in place (GnuCOBOL native convention), so
   * the record shown at the end of the loop carries normalized signs on the displayed fields.
   */
  static byte[] signNormalized(byte[] acct) {
    byte[] r = acct.clone();
    for (int off : new int[] {12, 24, 36, 78, 90}) {
      putZoned(r, off, zoned(r, off, 12, 2), 12, 2, true);
    }
    return r;
  }

  private static String label(String name) {
    return "%-24s:".formatted(name);
  }

  static byte[] populateOut(byte[] a, byte[] o) {
    copy(a, 0, o, 0, 12);
    putZoned(o, 12, zoned(a, 12, 12, 2), 12, 2, true);
    putZoned(o, 24, zoned(a, 24, 12, 2), 12, 2, true);
    putZoned(o, 36, zoned(a, 36, 12, 2), 12, 2, true);
    copy(a, 48, o, 48, 20);
    // COBDATFT type 2 -> 2: YYYY-MM-DD becomes YYYYMMDD, the last two bytes stay blank.
    byte[] reissue =
        (text(a, 68, 4) + text(a, 73, 2) + text(a, 76, 2) + "  ")
            .getBytes(StandardCharsets.ISO_8859_1);
    copy(reissue, 0, o, 68, 10);
    putZoned(o, 78, zoned(a, 78, 12, 2), 12, 2, true);
    if (zoned(a, 90, 12, 2).signum() == 0) {
      putPacked(o, 90, DEFAULT_CYC_DEBIT, 12, 2, true);
    }
    copy(a, 112, o, 97, 10);
    return o.clone();
  }

  static byte[] populateArray(byte[] a) {
    byte[] r = CobolData.spaces(ARRAY_LENGTH);
    copy(a, 0, r, 0, 11);
    BigDecimal bal = zoned(a, 12, 12, 2);
    for (int i = 0; i < 5; i++) {
      int off = 11 + i * 19;
      BigDecimal b = i < 2 ? bal : i == 2 ? ARRAY_BAL_3 : BigDecimal.ZERO;
      BigDecimal d = i < 3 ? ARRAY_DEBITS[i] : BigDecimal.ZERO;
      putZoned(r, off, b, 12, 2, true);
      putPacked(r, off + 12, d, 12, 2, true);
    }
    return r;
  }

  static byte[][] populateVb(byte[] a) {
    byte[] vb1 = new byte[12];
    copy(a, 0, vb1, 0, 12);
    byte[] vb2 = new byte[39];
    copy(a, 0, vb2, 0, 11);
    putZoned(vb2, 11, zoned(a, 12, 12, 2), 12, 2, true);
    putZoned(vb2, 23, zoned(a, 24, 12, 2), 12, 2, true);
    copy(a, 68, vb2, 35, 4);
    return new byte[][] {vb1, vb2};
  }
}
