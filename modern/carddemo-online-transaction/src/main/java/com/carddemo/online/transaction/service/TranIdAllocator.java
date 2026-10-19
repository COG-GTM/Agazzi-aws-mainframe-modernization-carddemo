package com.carddemo.online.transaction.service;

import com.carddemo.domain.entity.Transaction;
import com.carddemo.online.transaction.file.ReadResult;
import com.carddemo.online.transaction.file.Resp;
import com.carddemo.online.transaction.file.TransactFile;

/**
 * {@code MOVE HIGH-VALUES TO TRAN-ID, STARTBR, READPREV, ENDBR, ADD 1}: next TRAN-ID = last key + 1
 * (ENDFILE = zeros), PIC 9(16) without SIZE ERROR. The allocation runs under the V1300 lock row, so
 * concurrent tasks get consecutive ids instead of racing to the same one.
 */
public final class TranIdAllocator {

  private TranIdAllocator() {}

  /** Next id, or {@code null} when the browse fails ("Unable to lookup Transaction..."). */
  public static String next(TransactFile file) {
    file.lockForAppend();
    TransactBrowse browse = new TransactBrowse(file);
    browse.startbr(null);
    ReadResult<Transaction> last = browse.readPrev();
    browse.endbr();
    long n;
    if (last.resp() == Resp.NORMAL) {
      Long v = CobolFmt.zonedMove(last.record().getTranId());
      n = v == null ? 0 : v;
    } else if (last.resp() == Resp.ENDFILE) {
      n = 0;
    } else {
      return null;
    }
    return CobolFmt.digits((n + 1) % 10_000_000_000_000_000L, 16);
  }
}
