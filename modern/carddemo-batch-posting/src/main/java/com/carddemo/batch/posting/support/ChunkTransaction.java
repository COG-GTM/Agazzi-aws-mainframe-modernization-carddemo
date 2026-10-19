package com.carddemo.batch.posting.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Commit-per-chunk for a tasklet whose step runs without a transaction (Fase 5A, finding 5B F3):
 * {@link #run()} commits the current chunk and opens the next one. Like the VSAM updates of the
 * COBOL, committed chunks survive an abend: recovery is the legacy restore from backup.
 */
public final class ChunkTransaction implements Runnable {
  private final PlatformTransactionManager tm;
  private TransactionStatus status;
  private int commits;

  public ChunkTransaction(PlatformTransactionManager tm) {
    this.tm = tm;
    this.status = tm.getTransaction(new DefaultTransactionDefinition());
  }

  /** Commits the current chunk and begins the next one. */
  @Override
  public void run() {
    tm.commit(status);
    commits++;
    status = tm.getTransaction(new DefaultTransactionDefinition());
  }

  /** Commits the last chunk. */
  public void commit() {
    tm.commit(status);
    commits++;
  }

  public void rollback() {
    if (!status.isCompleted()) {
      tm.rollback(status);
    }
  }

  public int commits() {
    return commits;
  }
}
