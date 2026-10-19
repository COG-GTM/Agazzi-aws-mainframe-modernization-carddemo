package com.carddemo.batch.posting.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Finding 5B F3: one commit per chunk plus the last one; rollback only of the open chunk. */
class ChunkTransactionTest {

  static final class RecordingTm extends AbstractPlatformTransactionManager {
    final List<String> events = new ArrayList<>();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      events.add("begin");
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      events.add("commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      events.add("rollback");
    }
  }

  @Test
  void commitsEveryChunkAndTheTail() {
    var tm = new RecordingTm();
    var chunks = new ChunkTransaction(tm);
    chunks.run();
    chunks.run();
    chunks.commit();
    assertThat(chunks.commits()).isEqualTo(3);
    assertThat(tm.events).containsExactly("begin", "commit", "begin", "commit", "begin", "commit");
  }

  @Test
  void rollbackOnlyTouchesTheOpenChunk() {
    var tm = new RecordingTm();
    var chunks = new ChunkTransaction(tm);
    chunks.run();
    chunks.rollback();
    assertThat(tm.events).containsExactly("begin", "commit", "begin", "rollback");
    assertThat(chunks.commits()).isEqualTo(1);
  }
}
