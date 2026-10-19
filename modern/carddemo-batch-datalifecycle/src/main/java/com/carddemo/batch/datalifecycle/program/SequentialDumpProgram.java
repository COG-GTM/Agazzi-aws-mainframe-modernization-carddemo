package com.carddemo.batch.datalifecycle.program;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * CBACT02C (READCARD), CBACT03C (READXREF) and CBCUS01C (READCUST): read a KSDS sequentially and
 * DISPLAY every record.
 */
public final class SequentialDumpProgram {

  public static final SequentialDumpProgram CBACT02C =
      new SequentialDumpProgram(
          "CBACT02C",
          "ERROR OPENING CARDFILE",
          "ERROR READING CARDFILE",
          "ERROR CLOSING CARDFILE",
          false);
  public static final SequentialDumpProgram CBACT03C =
      new SequentialDumpProgram(
          "CBACT03C",
          "ERROR OPENING XREFFILE",
          "ERROR READING XREFFILE",
          "ERROR CLOSING XREFFILE",
          true);
  public static final SequentialDumpProgram CBCUS01C =
      new SequentialDumpProgram(
          "CBCUS01C",
          "ERROR OPENING CUSTFILE",
          "ERROR READING CUSTOMER FILE",
          "ERROR CLOSING CUSTOMER FILE",
          true);

  private final String name;
  private final String openError;
  private final String readError;
  private final String closeError;
  private final boolean displayInRead;

  private SequentialDumpProgram(
      String name, String openError, String readError, String closeError, boolean displayInRead) {
    this.name = name;
    this.openError = openError;
    this.readError = readError;
    this.closeError = closeError;
    this.displayInRead = displayInRead;
  }

  public String name() {
    return name;
  }

  /** Runs the program; returns the record count or throws {@link AbendException}. */
  public long run(Supplier<RecordSource> input, Sysout sysout) {
    sysout.display("START OF EXECUTION OF PROGRAM " + name);
    RecordSource source;
    try {
      source = Opener.open(input);
    } catch (FileStatusException e) {
      throw LegacyIo.abend(sysout, name, openError, e);
    }
    long count = 0;
    while (true) {
      Optional<byte[]> rec;
      try {
        rec = source.next();
      } catch (FileStatusException e) {
        throw LegacyIo.abend(sysout, name, readError, e);
      }
      if (rec.isEmpty()) {
        break;
      }
      if (displayInRead) {
        // CBACT03C / CBCUS01C also DISPLAY the record inside 1000-...-GET-NEXT.
        sysout.display(rec.get());
      }
      sysout.display(rec.get());
      count++;
    }
    try {
      source.close();
    } catch (FileStatusException e) {
      throw LegacyIo.abend(sysout, name, closeError, e);
    }
    sysout.display("END OF EXECUTION OF PROGRAM " + name);
    return count;
  }
}
