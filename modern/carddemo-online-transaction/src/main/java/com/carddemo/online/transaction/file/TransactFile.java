package com.carddemo.online.transaction.file;

import com.carddemo.domain.entity.Transaction;
import java.util.Optional;

/**
 * KSDS TRANSACT (CVTRA05Y) as seen by the O4 programs: keyed reads, the positioning primitives
 * behind STARTBR/READNEXT/READPREV and WRITE. Keys are the 16-byte {@code TRAN-ID}; an empty key
 * stands for LOW-VALUES.
 */
public interface TransactFile {

  ReadResult<Transaction> read(String tranId);

  Optional<Transaction> firstAtOrAfter(String key);

  Optional<Transaction> firstAfter(String key);

  Optional<Transaction> lastBefore(String key);

  Optional<Transaction> last();

  /**
   * Serializes "last TRAN-ID + 1" allocations until the end of the current unit of work (lock row
   * of V1300), the equivalent of the CICS ENQ on the TRANSACT browse/write.
   */
  void lockForAppend();

  /** CICS WRITE: NORMAL, DUPREC when the key exists, ERROR otherwise. */
  Resp write(Transaction transaction);
}
