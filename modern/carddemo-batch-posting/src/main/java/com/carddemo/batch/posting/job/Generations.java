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
