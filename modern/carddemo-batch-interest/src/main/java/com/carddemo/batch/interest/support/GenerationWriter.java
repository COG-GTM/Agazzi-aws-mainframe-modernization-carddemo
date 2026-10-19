package com.carddemo.batch.interest.support;

import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.GenerationStore.Generation;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * New GDG generation {@code DSN(+1)} with {@code DISP=(NEW,CATLG,DELETE)}: catalogued when the step
 * transaction commits, deleted when it rolls back. Records are written one per line (LRECL + LF),
 * the golden-master layout.
 */
public final class GenerationWriter implements AutoCloseable {

  private final GenerationStore store;
  private final Generation generation;
  private final OutputStream out;
  private int count;

  private GenerationWriter(GenerationStore store, Generation generation, OutputStream out) {
    this.store = store;
    this.generation = generation;
    this.out = out;
  }

  public static GenerationWriter open(GenerationStore store, String base) {
    Generation g = store.allocate(base);
    try {
      GenerationWriter w = new GenerationWriter(store, g, store.openOutput(g));
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(w.synchronization());
      }
      return w;
    } catch (IOException e) {
      store.discard(g);
      throw new UncheckedIOException(e);
    }
  }

  public void write(String record) {
    try {
      out.write(record.getBytes(StandardCharsets.US_ASCII));
      out.write('\n');
      count++;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Generation generation() {
    return generation;
  }

  public int count() {
    return count;
  }

  @Override
  public void close() {
    try {
      out.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private TransactionSynchronization synchronization() {
    return new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        close();
        if (status == STATUS_COMMITTED) {
          store.commit(generation);
        } else {
          store.discard(generation);
        }
      }
    };
  }
}
