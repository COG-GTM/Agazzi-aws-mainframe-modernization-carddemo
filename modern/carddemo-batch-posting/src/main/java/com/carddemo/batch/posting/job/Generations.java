package com.carddemo.batch.posting.job;

import com.carddemo.batch.posting.dataset.LineSequentialFile;
import com.carddemo.batch.posting.support.CobolAbendException;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.GenerationStore.Generation;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** GDG bases of the B1 jobs and line-sequential access to their generations. */
public final class Generations {

  public static final String DALYREJS = "AWS.M2.CARDDEMO.DALYREJS";
  public static final String TRANSACT_BKUP = "AWS.M2.CARDDEMO.TRANSACT.BKUP";
  public static final String SYSTRAN = "AWS.M2.CARDDEMO.SYSTRAN";
  public static final String TRANSACT_COMBINED = "AWS.M2.CARDDEMO.TRANSACT.COMBINED";

  private Generations() {}

  /** Writes a new generation (+1) and catalogs it (DISP=(NEW,CATLG,DELETE)). */
  public static Generation writeNew(GenerationStore store, String base, List<String> records) {
    Generation g = store.allocate(base);
    try (OutputStream out = store.openOutput(g)) {
      LineSequentialFile.write(out, records);
    } catch (IOException | RuntimeException e) {
      store.discard(g);
      throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
    }
    store.commit(g);
    return g;
  }

  /** Reads exactly {@code generation} (e.g. the one cataloged by a previous step of the job). */
  public static List<String> read(GenerationStore store, Generation generation, int lrecl) {
    if (!store.list(generation.base()).contains(generation)) {
      throw new CobolAbendException(12, "DATASET NOT FOUND " + generation.dsn(), null);
    }
    try (InputStream in = store.openInput(generation)) {
      return LineSequentialFile.read(in, lrecl, generation.dsn());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Catalogs {@code generation} when the step transaction commits and discards it on rollback, so a
   * published generation never outlives rolled-back database updates.
   */
  public static void commitWithTransaction(GenerationStore store, Generation generation) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      store.commit(generation);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_COMMITTED) {
              store.commit(generation);
            } else {
              store.discard(generation);
            }
          }
        });
  }

  /** Reads {@code file} if given, else generation 0 of {@code base}. */
  public static List<String> readCurrent(GenerationStore store, String base, Path file, int lrecl) {
    if (file != null) {
      return LineSequentialFile.read(file, lrecl);
    }
    Generation g =
        store
            .resolve(base, 0)
            .orElseThrow(
                () -> new CobolAbendException(12, "DATASET NOT FOUND " + base + "(0)", null));
    try (InputStream in = store.openInput(g)) {
      return LineSequentialFile.read(in, lrecl, g.dsn());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
