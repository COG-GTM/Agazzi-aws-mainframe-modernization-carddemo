package com.carddemo.batch.posting.idcams;

import com.carddemo.batch.posting.dataset.Keys;
import com.carddemo.contracts.record.TransactionRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** DFSORT / IDCAMS functions used by COMBTRAN and TRANIDX on CVTRA05Y records. */
public final class TransactionUtilities {

  /** TRANIDX AIX: KEYS(26 304) = TRAN-PROC-TS. */
  public static final int AIX_KEY_OFFSET = 304;

  public static final int AIX_KEY_LENGTH = 26;

  private TransactionUtilities() {}

  /**
   * COMBTRAN STEP05R: {@code SORT FIELDS=(TRAN-ID,A)} with {@code TRAN-ID,1,16,CH}; equal keys keep
   * input order (SORTIN concatenation order).
   */
  public static List<String> sortByTranId(List<List<String>> sortin) {
    List<String> all = new ArrayList<>();
    sortin.forEach(all::addAll);
    all.sort(Comparator.comparing(r -> Keys.pic(r, 16)));
    return all;
  }

  /**
   * TRANIDX STEP30 BLDINDEX: non-unique alternate index entries {@code TRAN-PROC-TS + TRAN-ID} in
   * alternate-key sequence (primary keys ascending within the same alternate key).
   */
  public static List<String> alternateIndex(List<TransactionRecord> base) {
    return base.stream()
        .map(t -> Keys.pic(t.tranProcTs(), AIX_KEY_LENGTH) + Keys.pic(t.tranId(), 16))
        .sorted()
        .toList();
  }
}
