package com.carddemo.batch.posting.job;

import com.carddemo.batch.posting.cbtrn01c.Cbtrn01cVerifier;
import com.carddemo.batch.posting.cbtrn02c.Cbtrn02cPoster;
import com.carddemo.batch.posting.cbtrn02c.PostingResult;
import com.carddemo.batch.posting.dataset.DuplicateKeyException;
import com.carddemo.batch.posting.dataset.FileDatasets;
import com.carddemo.batch.posting.dataset.LineSequentialFile;
import com.carddemo.batch.posting.dataset.PostingDatasets;
import com.carddemo.batch.posting.idcams.TransactionUtilities;
import com.carddemo.batch.posting.support.CobolAbendException;
import com.carddemo.batch.posting.support.ReturnCodes;
import com.carddemo.batch.posting.support.Sysout;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.GenerationStore.Generation;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.copybook.CoreCodecs;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.ObjectProvider;

/** Tasklets of the B1 JCL steps (one class per {@code EXEC}). */
final class PostingTasklets {

  private static final int LRECL_TRAN = 350;

  /** Job-context key: generation of TRANSACT.COMBINED(+1) cataloged by COMBTRAN STEP05R. */
  static final String COMBINED_GENERATION = "combinedGeneration";

  private PostingTasklets() {}

  /** POSTTRAN STEP15 EXEC PGM=CBTRN02C; DALYREJS DD DSN=...DALYREJS(+1). */
  static final class PosttranStep15 extends JclStepTasklet {
    private final PostingDatasetsFactory datasets;
    private final GenerationStore generations;
    private final BusinessClock clock;

    PosttranStep15(
        PostingDatasetsFactory datasets, GenerationStore generations, BusinessClock clock) {
      super(Cbtrn02cPoster.PROGRAM);
      this.datasets = datasets;
      this.generations = generations;
      this.clock = clock;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      PostingDatasets ds = datasets.open(params);
      Generation rejs = generations.allocate(Generations.DALYREJS);
      List<String> rejects = new ArrayList<>();
      try {
        PostingResult result = new Cbtrn02cPoster(clock).run(ds, rejects::add, sysout);
        try (OutputStream out = generations.openOutput(rejs)) {
          LineSequentialFile.write(out, rejects);
        }
        ds.flush();
        Generations.commitWithTransaction(generations, rejs);
        step.getExecutionContext().putInt("processed", result.processed());
        step.getExecutionContext().putInt("rejected", result.rejected());
        step.getExecutionContext().putString("dalyrejs", rejs.dsn());
        step.setWriteCount(result.processed() - result.rejected());
        step.setWriteSkipCount(result.rejected());
        return result.returnCode();
      } catch (IOException e) {
        generations.discard(rejs);
        throw new UncheckedIOException(e);
      } catch (RuntimeException e) {
        generations.discard(rejs);
        throw e;
      }
    }
  }

  /** CBTRN01C (no JCL in app/jcl): DALYTRAN card/account verification report. */
  static final class Cbtrn01cStep extends JclStepTasklet {
    private final PostingDatasetsFactory datasets;

    Cbtrn01cStep(PostingDatasetsFactory datasets) {
      super(Cbtrn01cVerifier.PROGRAM);
      this.datasets = datasets;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      return new Cbtrn01cVerifier().run(datasets.open(params), sysout);
    }
  }

  /** DALYREJS STEP05 IDCAMS DEFINE GDG NAME(AWS.M2.CARDDEMO.DALYREJS) LIMIT(5) SCRATCH. */
  static final class DalyrejsStep05 extends JclStepTasklet {
    static final int LIMIT = 5;
    private final GenerationStore generations;

    DalyrejsStep05(GenerationStore generations) {
      super("IDCAMS");
      this.generations = generations;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      sysout.display(
          "DEFINE GENERATIONDATAGROUP (NAME(" + Generations.DALYREJS + ") LIMIT(5) SCRATCH)");
      int limit = generations.limit(Generations.DALYREJS);
      if (limit != LIMIT) {
        sysout.display("IDC3013I GDG BASE LIMIT IS " + limit + ", EXPECTED " + LIMIT);
        return 8;
      }
      sysout.display(
          "IDC0001I FUNCTION COMPLETED, HIGHEST CONDITION CODE WAS 0 ("
              + generations.list(Generations.DALYREJS).size()
              + " GENERATIONS CATALOGED)");
      return 0;
    }
  }

  /** TRANBKP STEP05R EXEC PROC=REPROC: REPRO TRANSACT.VSAM.KSDS → TRANSACT.BKUP(+1). */
  static final class TranbkpStep05r extends JclStepTasklet {
    private final PostingDatasetsFactory datasets;
    private final GenerationStore generations;

    TranbkpStep05r(PostingDatasetsFactory datasets, GenerationStore generations) {
      super("IDCAMS");
      this.datasets = datasets;
      this.generations = generations;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      if (params.fileStorage()
          && !java.nio.file.Files.exists(
              params.datasetDir().resolve(FileDatasets.TRANSACT + ".dat"))) {
        sysout.display("IDC3012I ENTRY AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS NOT FOUND");
        throw new CobolAbendException(12, "TRANSACT.VSAM.KSDS NOT FOUND", "35");
      }
      List<TransactionRecord> recs = datasets.open(params).transactions().records();
      Generation g =
          Generations.writeNew(
              generations,
              Generations.TRANSACT_BKUP,
              recs.stream().map(CoreCodecs.TRANSACTION::encodeLine).toList());
      sysout.display("REPRO INFILE(FILEIN) OUTFILE(FILEOUT)");
      sysout.display("IDC0005I NUMBER OF RECORDS PROCESSED WAS " + recs.size());
      step.getExecutionContext().putString("backup", g.dsn());
      step.setWriteCount(recs.size());
      return 0;
    }
  }

  /** TRANBKP STEP05: DELETE TRANSACT.VSAM.KSDS CLUSTER and .AIX (IF MAXCC LE 08 SET MAXCC=0). */
  static final class TranbkpStep05 extends JclStepTasklet {
    private final PostingDatasetsFactory datasets;

    TranbkpStep05(PostingDatasetsFactory datasets) {
      super("IDCAMS");
      this.datasets = datasets;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      PostingDatasets ds = datasets.open(params);
      ds.transactions().clear();
      ds.flush();
      if (params.fileStorage()) {
        try {
          java.nio.file.Files.deleteIfExists(params.datasetDir().resolve(AIX_FILE));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
      sysout.display("IDC0550I ENTRY (C) AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS DELETED");
      return 0;
    }
  }

  /** TRANBKP STEP10 EXEC PGM=IDCAMS,COND=(4,LT): DEFINE CLUSTER TRANSACT.VSAM.KSDS. */
  static final class TranbkpStep10 extends JclStepTasklet {
    private final PostingDatasetsFactory datasets;

    TranbkpStep10(PostingDatasetsFactory datasets) {
      super("IDCAMS");
      this.datasets = datasets;
    }

    @Override
    protected boolean bypass(StepExecution step) {
      return ReturnCodes.maxPreviousRc(step) > 4;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      PostingDatasets ds = datasets.open(params);
      if (params.fileStorage()
          && !java.nio.file.Files.exists(params.datasetDir().resolve("TRANSACT.dat"))) {
        LineSequentialFile.write(params.datasetDir().resolve("TRANSACT.dat"), List.of());
      }
      sysout.display(
          "DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) KEYS(16 0) RECORDSIZE(350 350))");
      sysout.display("CLUSTER RECORDS: " + ds.transactions().records().size());
      return 0;
    }
  }

  /** COMBTRAN STEP05R SORT: TRANSACT.BKUP(0) + SYSTRAN(0) by TRAN-ID → COMBINED(+1). */
  static final class CombtranStep05r extends JclStepTasklet {
    private final GenerationStore generations;

    CombtranStep05r(GenerationStore generations) {
      super("SORT");
      this.generations = generations;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      List<String> bkup =
          Generations.readCurrent(
              generations, Generations.TRANSACT_BKUP, params.tranbkup(), LRECL_TRAN);
      List<String> systran =
          Generations.readCurrent(generations, Generations.SYSTRAN, params.systran(), LRECL_TRAN);
      List<String> sorted = TransactionUtilities.sortByTranId(List.of(bkup, systran));
      Generation g = Generations.writeNew(generations, Generations.TRANSACT_COMBINED, sorted);
      sysout.display(
          String.format(
              Locale.ROOT,
              "ICE054I 0 RECORDS - IN: %d, OUT: %d (%s)",
              bkup.size() + systran.size(),
              sorted.size(),
              g.dsn()));
      step.getExecutionContext().putString("combined", g.dsn());
      step.getJobExecution().getExecutionContext().putInt(COMBINED_GENERATION, g.number());
      step.setWriteCount(sorted.size());
      return 0;
    }
  }

  /** COMBTRAN STEP10 IDCAMS REPRO INFILE(TRANSACT.COMBINED(+1)) OUTFILE(TRANSACT.VSAM.KSDS). */
  static final class CombtranStep10 extends JclStepTasklet {
    private final PostingDatasetsFactory datasets;
    private final GenerationStore generations;

    CombtranStep10(PostingDatasetsFactory datasets, GenerationStore generations) {
      super("IDCAMS");
      this.datasets = datasets;
      this.generations = generations;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      ExecutionContext job = step.getJobExecution().getExecutionContext();
      List<String> combined =
          job.containsKey(COMBINED_GENERATION)
              ? Generations.read(
                  generations,
                  new Generation(Generations.TRANSACT_COMBINED, job.getInt(COMBINED_GENERATION)),
                  LRECL_TRAN)
              : Generations.readCurrent(
                  generations, Generations.TRANSACT_COMBINED, null, LRECL_TRAN);
      PostingDatasets ds = datasets.open(params);
      ds.transactions().open();
      int n = 0;
      for (String line : combined) {
        TransactionRecord r = CoreCodecs.TRANSACTION.decodeLine(line);
        try {
          ds.transactions().write(r);
        } catch (DuplicateKeyException e) {
          sysout.display("IDC3302I ACTION ERROR ON AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS");
          sysout.display("IDC3308I ** DUPLICATE RECORD - KEY FOLLOWS: " + r.tranId().strip());
          throw new CobolAbendException(12, e.getMessage(), "22");
        }
        n++;
      }
      ds.flush();
      sysout.display("IDC0005I NUMBER OF RECORDS PROCESSED WAS " + n);
      step.setWriteCount(n);
      return 0;
    }
  }

  static final String AIX_FILE = FileDatasets.TRANSACT_AIX_FILE;

  /** TRANIDX STEP20: DEFINE ALTERNATEINDEX KEYS(26 304) NONUNIQUEKEY UPGRADE. */
  static final class TranidxStep20 extends JclStepTasklet {
    static final String INDEX = "tran_proc_ts";
    private final ObjectProvider<DataSource> dataSource;

    TranidxStep20(ObjectProvider<DataSource> dataSource) {
      super("IDCAMS");
      this.dataSource = dataSource;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      sysout.display(
          "DEFINE ALTERNATEINDEX (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX) KEYS(26 304)"
              + " NONUNIQUEKEY UPGRADE)");
      if (params.fileStorage()) {
        return 0;
      }
      try (Connection c = dataSource.getObject().getConnection();
          ResultSet rs = c.getMetaData().getIndexInfo(null, null, "transaction", false, true)) {
        while (rs.next()) {
          if (INDEX.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
            sysout.display("AIX MAPPED TO INDEX " + INDEX + " ON transaction(tran_proc_ts)");
            return 0;
          }
        }
      } catch (SQLException e) {
        throw new CobolAbendException(12, "IDC3009I " + e.getMessage(), null);
      }
      sysout.display("IDC3014I INDEX " + INDEX + " NOT FOUND ON transaction");
      return 12;
    }
  }

  /** TRANIDX STEP25: DEFINE PATH ... PATHENTRY(...AIX) — the index is the access path. */
  static final class TranidxStep25 extends JclStepTasklet {
    TranidxStep25() {
      super("IDCAMS");
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      sysout.display(
          "DEFINE PATH (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH)"
              + " PATHENTRY(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX))");
      return 0;
    }
  }

  /** TRANIDX STEP30: BLDINDEX INDATASET(TRANSACT.VSAM.KSDS) OUTDATASET(TRANSACT.VSAM.AIX). */
  static final class TranidxStep30 extends JclStepTasklet {
    private final PostingDatasetsFactory datasets;

    TranidxStep30(PostingDatasetsFactory datasets) {
      super("IDCAMS");
      this.datasets = datasets;
    }

    @Override
    protected int run(PostingJobParameters params, Sysout sysout, StepExecution step) {
      List<String> aix =
          TransactionUtilities.alternateIndex(datasets.open(params).transactions().records());
      if (params.fileStorage()) {
        LineSequentialFile.write(params.datasetDir().resolve(AIX_FILE), aix);
      }
      sysout.display("IDC0652I AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX SUCCESSFULLY BUILT");
      sysout.display("AIX ENTRIES: " + aix.size());
      step.setWriteCount(aix.size());
      return 0;
    }
  }
}
