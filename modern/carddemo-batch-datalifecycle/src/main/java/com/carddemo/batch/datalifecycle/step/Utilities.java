package com.carddemo.batch.datalifecycle.step;

import com.carddemo.batch.datalifecycle.gdg.GdgCatalog;
import com.carddemo.batch.datalifecycle.io.RecordFiles;
import com.carddemo.batch.datalifecycle.io.RecordFormat;
import com.carddemo.batch.datalifecycle.job.ConditionCodeException;
import com.carddemo.batch.datalifecycle.job.LegacyStep;
import com.carddemo.batch.datalifecycle.job.StepEnv;
import com.carddemo.batch.datalifecycle.program.FileStatusException;
import com.carddemo.batch.datalifecycle.program.RecordSink;
import com.carddemo.batch.datalifecycle.store.KsdsTable;
import com.carddemo.batch.datalifecycle.store.TableStore;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.GenerationStore.Generation;
import com.carddemo.contracts.port.MaintenanceModePort;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntToLongFunction;
import java.util.function.UnaryOperator;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.dao.DataIntegrityViolationException;

/** IDCAMS, IEFBR14, IEBGENER and SDSF/CEMT steps of the data lifecycle JCL. */
public final class Utilities {

  public static final String IDCAMS = "IDCAMS";
  public static final String IEFBR14 = "IEFBR14";
  public static final String IEBGENER = "IEBGENER";
  public static final String SDSF = "SDSF";

  static final String COMPLETED = "IDC0001I FUNCTION COMPLETED, HIGHEST CONDITION CODE WAS ";

  private final TableStore tables;
  private final GdgCatalog gdgs;
  private final GenerationStore generations;
  private final MaintenanceModePort maintenance;

  public Utilities(
      TableStore tables,
      GdgCatalog gdgs,
      GenerationStore generations,
      MaintenanceModePort maintenance) {
    this.tables = tables;
    this.gdgs = gdgs;
    this.generations = generations;
    this.maintenance = maintenance;
  }

  /** {@code DELETE <ksds> [AIX]} followed by {@code IF MAXCC LE 08 THEN SET MAXCC = 0}. */
  public LegacyStep deleteCluster(KsdsTable table) {
    return env -> {
      int rows = tables.clear(table);
      env.sysout().display("IDC0550I ENTRY (C) " + table.dsn() + " DELETED (" + rows + " RECORDS)");
      env.sysout().display(COMPLETED + 0);
      return 0;
    };
  }

  /** {@code DEFINE CLUSTER}: RC 12 (duplicate data set name) when the cluster still has data. */
  public LegacyStep defineCluster(KsdsTable table) {
    return env -> defineEmpty(env, table.dsn(), tables.count(table));
  }

  /** DELETE + {@code SET MAXCC = 0} + DEFINE of a cluster in the same IDCAMS step. */
  public LegacyStep redefineCluster(KsdsTable table) {
    return env -> {
      int rows = tables.clear(table);
      env.sysout().display("IDC0550I ENTRY (C) " + table.dsn() + " DELETED (" + rows + " RECORDS)");
      return defineEmpty(env, table.dsn(), tables.count(table));
    };
  }

  /** As {@link #redefineCluster(KsdsTable)} for the ESDS / RRDS demo tables. */
  public LegacyStep redefineCluster(String dsn, String table) {
    return env -> {
      int rows = tables.clear(table);
      env.sysout().display("IDC0550I ENTRY (C) " + dsn + " DELETED (" + rows + " RECORDS)");
      return defineEmpty(env, dsn, tables.count(table));
    };
  }

  private static int defineEmpty(StepEnv env, String dsn, long rows) {
    if (rows > 0) {
      throw new ConditionCodeException(
          12, "IGD17101I DATA SET " + dsn + " NOT DEFINED BECAUSE DUPLICATE NAME EXISTS");
    }
    env.sysout().display("IDC0508I DATA ALLOCATION STATUS FOR " + dsn + " IS 0");
    env.sysout().display(COMPLETED + 0);
    return 0;
  }

  /** {@code REPRO INFILE(<dd>) OUTFILE(<ksds>)} from the PS file of DD {@code inDd}. */
  public LegacyStep repro(String inDd, String inDsn, KsdsTable table, int lrecl) {
    return repro(inDd, inDsn, table, lrecl, UnaryOperator.identity());
  }

  public LegacyStep repro(
      String inDd, String inDsn, KsdsTable table, int lrecl, UnaryOperator<Record> transform) {
    return env -> {
      List<byte[]> records = read(env, inDd, inDsn, lrecl);
      int keyLen = keyLength(table);
      for (int i = 1; i < records.size(); i++) {
        if (Arrays.compareUnsigned(records.get(i - 1), 0, keyLen, records.get(i), 0, keyLen) >= 0) {
          throw new ConditionCodeException(
              12, "IDC3314I **RECORD OUT OF SEQUENCE OR DUPLICATE KEY ON " + table.dsn());
        }
      }
      try {
        tables.load(table, records, transform);
      } catch (DataIntegrityViolationException e) {
        throw new ConditionCodeException(
            12, "IDC3314I **RECORD OUT OF SEQUENCE OR DUPLICATE KEY ON " + table.dsn(), e);
      }
      return reproEnd(env, records.size());
    };
  }

  static int keyLength(KsdsTable table) {
    return switch (table) {
      case ACCTDATA -> 11;
      case CARDDATA, CARDXREF, TRANSACT -> 16;
      case CUSTDATA -> 9;
      case TRANTYPE -> 2;
      case TRANCATG -> 6;
      case USRSEC -> 8;
    };
  }

  /** REPRO of CSUSR01Y records into the ESDS (RBA) or RRDS (RRN) demo table. */
  public LegacyStep reproUserSecurity(
      String inDd, String inDsn, String table, String keyColumn, IntToLongFunction key) {
    return env -> {
      List<byte[]> records = read(env, inDd, inDsn, 80);
      try {
        tables.loadUserSecurity(table, keyColumn, records, key);
      } catch (DataIntegrityViolationException e) {
        throw new ConditionCodeException(12, "IDC3302I ACTION ERROR ON " + table, e);
      }
      return reproEnd(env, records.size());
    };
  }

  private static int reproEnd(StepEnv env, int n) {
    env.sysout().display("IDC0005I NUMBER OF RECORDS PROCESSED WAS " + n);
    // IDCAMS ends an empty REPRO with condition code 4 (IDC3005I).
    int rc = n == 0 ? 4 : 0;
    env.sysout().display(COMPLETED + rc);
    return rc;
  }

  static List<byte[]> read(StepEnv env, String dd, String dsn, int lrecl) {
    Path path = env.dataset(dd, dsn);
    if (!Files.exists(path)) {
      throw new ConditionCodeException(12, "IEF212I " + dd + " - DATA SET NOT FOUND: " + path);
    }
    try {
      return RecordFiles.readAll(path, lrecl, RecordFormat.of(path));
    } catch (FileStatusException e) {
      throw new ConditionCodeException(
          12,
          "IDC3302I ACTION ERROR ON " + dsn + " (STATUS " + e.status() + ") " + e.getMessage(),
          e);
    }
  }

  /** {@code DEFINE ALTERNATEINDEX} / {@code DEFINE PATH}: the V1 secondary index. */
  public LegacyStep defineIndex(String what, String dsn, String index) {
    return env -> {
      env.sysout().display("IDC0508I " + what + " " + dsn + " -> INDEX " + index);
      env.sysout().display(COMPLETED + 0);
      return 0;
    };
  }

  /** {@code BLDINDEX}: the V1 index is maintained by the database, report the entries. */
  public LegacyStep bldIndex(KsdsTable table, String aix) {
    return env -> {
      long n = tables.count(table);
      env.sysout().display("IDC0652I " + aix + " SUCCESSFULLY BUILT");
      env.sysout().display("IDC0005I NUMBER OF RECORDS PROCESSED WAS " + n);
      env.sysout().display(COMPLETED + 0);
      return 0;
    };
  }

  /** {@code CEMT SET FIL(..) CLO|OPE} issued through SDSF: online maintenance window. */
  /** Reopens the CICS files closed by CLCIFIL when the job does not reach OPCIFIL. */
  public JobExecutionListener reopenOnFailure(String... files) {
    return new JobExecutionListener() {
      @Override
      public void afterJob(JobExecution execution) {
        if (execution.getStatus() != BatchStatus.COMPLETED) {
          maintenance.open(new LinkedHashSet<>(List.of(files)));
        }
      }
    };
  }

  public LegacyStep cics(boolean close, String... files) {
    return env -> {
      Set<String> set = new LinkedHashSet<>(List.of(files));
      if (close) {
        maintenance.close(set);
      } else {
        maintenance.open(set);
      }
      for (String f : files) {
        env.sysout().display("CEMT SET FIL(%-8s) %s NORMAL".formatted(f, close ? "CLO" : "OPE"));
      }
      return 0;
    };
  }

  /** IEFBR14 with {@code DISP=(MOD,DELETE,DELETE)} on each DD. */
  public LegacyStep deleteDatasets(String... ddAndDsn) {
    return env -> {
      for (int i = 0; i < ddAndDsn.length; i += 2) {
        Files.deleteIfExists(env.dataset(ddAndDsn[i], ddAndDsn[i + 1]));
      }
      return 0;
    };
  }

  /** IEBGENER of in-stream SYSUT1 records ({@code DD *}) to a new PS dataset. */
  public LegacyStep generateInstream(String outDd, String outDsn, int lrecl, List<String> lines) {
    return env -> {
      Path out = env.dataset(outDd, outDsn);
      List<byte[]> recs = new ArrayList<>();
      for (String line : lines) {
        byte[] r = com.carddemo.batch.datalifecycle.cobol.CobolData.spaces(lrecl);
        com.carddemo.batch.datalifecycle.cobol.CobolData.putText(r, 0, lrecl, line);
        recs.add(r);
      }
      try (RecordSink sink = RecordFiles.create(out)) {
        for (byte[] r : recs) {
          sink.write(r);
        }
      } catch (UncheckedIOException e) {
        throw new ConditionCodeException(12, "IEC143I OPEN FAILED FOR " + outDd, e);
      }
      return iebgenerEnd(env, recs.size());
    };
  }

  /** {@code DEFINE GENERATIONDATAGROUP}; {@code tolerateDuplicate} for {@code IF LASTCC=12}. */
  public LegacyStep defineGdgs(int limit, boolean tolerateDuplicate, String... bases) {
    return env -> {
      int max = 0;
      for (String base : bases) {
        int rc = gdgs.define(base, limit, true);
        env.sysout()
            .display(
                rc == 0
                    ? "IDC0508I GDG BASE " + base + " DEFINED LIMIT(" + limit + ")"
                    : "IDC3013I DUPLICATE DATA SET NAME " + base);
        if (rc != 0 && tolerateDuplicate) {
          rc = 0;
        }
        max = Math.max(max, rc);
      }
      env.sysout().display(COMPLETED + max);
      return max;
    };
  }

  /** IEBGENER SYSUT1 (PS) to SYSUT2 {@code <base>(+1)} through the GenerationStore. */
  public LegacyStep copyToNewGeneration(String inDsn, String base) {
    return env -> {
      Path in = env.dataset("SYSUT1", inDsn);
      if (!Files.exists(in)) {
        throw new ConditionCodeException(12, "IEF212I SYSUT1 - DATA SET NOT FOUND: " + in);
      }
      Generation g = generations.allocate(base);
      try (OutputStream out = generations.openOutput(g)) {
        Files.copy(in, out);
      } catch (IOException e) {
        generations.discard(g);
        throw new ConditionCodeException(12, "IEC020I I/O ERROR ON SYSUT2 " + g.dsn(), e);
      }
      generations.commit(g);
      env.sysout().display("IEB352I SYSUT2 " + g.dsn() + " CATALOGED");
      return iebgenerEnd(env, -1);
    };
  }

  private static int iebgenerEnd(StepEnv env, int records) {
    env.sysout().display("DATA SET UTILITY - GENERATE");
    if (records >= 0) {
      env.sysout().display("RECORDS PROCESSED: " + records);
    }
    env.sysout().display("PROCESSING ENDED AT EOD");
    return 0;
  }

  /** Deletes and re-creates an empty output cluster file (CBEXPORT STEP01). */
  public LegacyStep redefineFile(String dd, String dsn) {
    return env -> {
      Path p = env.dataset(dd, dsn);
      boolean existed = Files.deleteIfExists(p);
      if (existed) {
        env.sysout().display("IDC0550I ENTRY (C) " + dsn + " DELETED");
      }
      if (p.getParent() != null) {
        Files.createDirectories(p.getParent());
      }
      Files.write(p, new byte[0]);
      env.sysout().display("IDC0508I DATA ALLOCATION STATUS FOR " + dsn + " IS 0");
      env.sysout().display(COMPLETED + 0);
      return 0;
    };
  }
}
