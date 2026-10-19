package com.carddemo.batch.datalifecycle.step;

import com.carddemo.batch.datalifecycle.export.CbExport;
import com.carddemo.batch.datalifecycle.export.CbImport;
import com.carddemo.batch.datalifecycle.io.RecordFiles;
import com.carddemo.batch.datalifecycle.io.RecordFormat;
import com.carddemo.batch.datalifecycle.job.LegacyStep;
import com.carddemo.batch.datalifecycle.job.StepEnv;
import com.carddemo.batch.datalifecycle.program.Cbact01c;
import com.carddemo.batch.datalifecycle.program.RecordSink;
import com.carddemo.batch.datalifecycle.program.RecordSource;
import com.carddemo.batch.datalifecycle.program.SequentialDumpProgram;
import com.carddemo.batch.datalifecycle.store.KsdsTable;
import com.carddemo.batch.datalifecycle.store.TableStore;
import com.carddemo.contracts.port.BusinessClock;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * COBOL program steps. A KSDS DD reads the V1 table in key order unless the DD is overridden with a
 * file ({@code dd.<DDNAME>=<path>}).
 */
public final class Programs {

  public static final String EXPORT_DSN = "AWS.M2.CARDDEMO.EXPORT.DATA";

  private final TableStore tables;
  private final BusinessClock clock;

  public Programs(TableStore tables, BusinessClock clock) {
    this.tables = tables;
    this.clock = clock;
  }

  Supplier<RecordSource> ksds(StepEnv env, String dd, KsdsTable table, int lrecl, int keyLen) {
    return () ->
        env.dd(dd)
            .map(p -> RecordFiles.open(p, lrecl, 0, keyLen))
            .orElseGet(() -> RecordFiles.source(tables.unload(table)));
  }

  private static Supplier<RecordSink> out(StepEnv env, String dd, String dsn) {
    return () -> RecordFiles.create(env.dataset(dd, dsn));
  }

  /** READACCT STEP05: CBACT01C writes PSCOMP, ARRYPS and VBPS. */
  public LegacyStep cbact01c() {
    return env -> {
      new Cbact01c()
          .run(
              ksds(env, "ACCTFILE", KsdsTable.ACCTDATA, 300, 11),
              () ->
                  new Cbact01c.Outputs(
                      RecordFiles.create(
                          env.dataset("OUTFILE", "AWS.M2.CARDDEMO.ACCTDATA.PSCOMP"),
                          RecordFormat.FIXED),
                      RecordFiles.create(
                          env.dataset("ARRYFILE", "AWS.M2.CARDDEMO.ACCTDATA.ARRYPS"),
                          RecordFormat.FIXED),
                      RecordFiles.create(
                          env.dataset("VBRCFILE", "AWS.M2.CARDDEMO.ACCTDATA.VBPS"),
                          RecordFormat.VARIABLE)),
              env.sysout());
      return 0;
    };
  }

  /** READCARD / READXREF / READCUST: sequential dump of a KSDS to SYSOUT. */
  public LegacyStep dump(
      SequentialDumpProgram program, String dd, KsdsTable table, int lrecl, int keyLen) {
    return env -> {
      program.run(ksds(env, dd, table, lrecl, keyLen), env.sysout());
      return 0;
    };
  }

  /** CBEXPORT STEP02. */
  public LegacyStep cbexport() {
    return env -> {
      new CbExport(clock)
          .run(
              new CbExport.Inputs(
                  ksds(env, "CUSTFILE", KsdsTable.CUSTDATA, 500, 9),
                  ksds(env, "ACCTFILE", KsdsTable.ACCTDATA, 300, 11),
                  ksds(env, "XREFFILE", KsdsTable.CARDXREF, 50, 16),
                  ksds(env, "TRANSACT", KsdsTable.TRANSACT, 350, 16),
                  ksds(env, "CARDFILE", KsdsTable.CARDDATA, 150, 16)),
              out(env, "EXPFILE", EXPORT_DSN),
              env.sysout());
      return 0;
    };
  }

  /**
   * CBIMPORT STEP01; the legacy JCL does not allocate CARDOUT, so it defaults to CARDDATA.IMPORT.
   */
  public LegacyStep cbimport() {
    return env -> {
      Path in = env.dataset("EXPFILE", EXPORT_DSN);
      Supplier<RecordSink> cards = out(env, "CARDOUT", "AWS.M2.CARDDEMO.CARDDATA.IMPORT");
      new CbImport(clock)
          .run(
              () -> RecordFiles.open(in, 500, 27, 4),
              new CbImport.Outputs(
                  out(env, "CUSTOUT", "AWS.M2.CARDDEMO.CUSTDATA.IMPORT"),
                  out(env, "ACCTOUT", "AWS.M2.CARDDEMO.ACCTDATA.IMPORT"),
                  out(env, "XREFOUT", "AWS.M2.CARDDEMO.CARDXREF.IMPORT"),
                  out(env, "TRNXOUT", "AWS.M2.CARDDEMO.TRANSACT.IMPORT"),
                  cards,
                  out(env, "ERROUT", "AWS.M2.CARDDEMO.IMPORT.ERRORS")),
              env.sysout());
      return 0;
    };
  }
}
