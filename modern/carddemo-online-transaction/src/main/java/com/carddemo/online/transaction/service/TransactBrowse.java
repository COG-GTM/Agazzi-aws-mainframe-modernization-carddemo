package com.carddemo.online.transaction.service;

import com.carddemo.domain.entity.Transaction;
import com.carddemo.online.transaction.file.ReadResult;
import com.carddemo.online.transaction.file.Resp;
import com.carddemo.online.transaction.file.TransactFile;
import java.util.Optional;

/**
 * STARTBR / READNEXT / READPREV / ENDBR on TRANSACT with CICS semantics: STARTBR is GTEQ (NOTFND
 * when no key is &gt;= RIDFLD), HIGH-VALUES ({@code null}) positions after the last record, the
 * first READPREV after STARTBR returns the record at the start position, reads without an active
 * browse fail (INVREQ, reported as ERROR).
 */
public final class TransactBrowse {

  private final TransactFile file;
  private boolean active;
  private boolean highValues;
  private String position;
  private String lastKey;

  public TransactBrowse(TransactFile file) {
    this.file = file;
  }

  /** {@code key == null} = HIGH-VALUES, {@code ""} = LOW-VALUES. */
  public Resp startbr(String key) {
    active = false;
    lastKey = null;
    highValues = key == null;
    if (highValues) {
      active = true;
      return Resp.NORMAL;
    }
    if (file.firstAtOrAfter(key).isEmpty()) {
      return Resp.NOTFND;
    }
    position = key;
    active = true;
    return Resp.NORMAL;
  }

  public ReadResult<Transaction> readNext() {
    if (!active) {
      return ReadResult.of(Resp.ERROR);
    }
    Optional<Transaction> r;
    if (lastKey != null) {
      r = file.firstAfter(lastKey);
    } else {
      r = highValues ? Optional.empty() : file.firstAtOrAfter(position);
    }
    return found(r);
  }

  public ReadResult<Transaction> readPrev() {
    if (!active) {
      return ReadResult.of(Resp.ERROR);
    }
    Optional<Transaction> r;
    if (lastKey != null) {
      r = file.lastBefore(lastKey);
    } else {
      r = highValues ? file.last() : file.firstAtOrAfter(position);
    }
    return found(r);
  }

  public void endbr() {
    active = false;
  }

  private ReadResult<Transaction> found(Optional<Transaction> r) {
    if (r.isEmpty()) {
      return ReadResult.of(Resp.ENDFILE);
    }
    lastKey = r.get().getTranId();
    return ReadResult.normal(r.get());
  }
}
