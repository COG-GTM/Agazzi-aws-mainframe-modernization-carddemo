package com.carddemo.ext.authorization.batch;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.model.PendingAuthSummary;
import com.carddemo.ext.authorization.store.AuthKey;
import com.carddemo.ext.authorization.store.PendingAuthDatabase;
import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CBPAUP0C: deletes expired PAUTDTL1 segments and the PAUTSUM0 roots left without approved
 * authorizations. As in the COBOL, count/amount decrements are kept in memory only (the root is
 * never REPLaced) and the root deletion tests {@code PA-APPROVED-AUTH-CNT} only.
 */
@Service
@ConditionalOnAuthorizationExtension
public class PendingAuthPurgeService {

  private final PendingAuthDatabase db;
  private final BusinessClock clock;

  public PendingAuthPurgeService(PendingAuthDatabase db, BusinessClock clock) {
    this.db = db;
    this.clock = clock;
  }

  /** {@code ACCEPT ... FROM DAY}: YYDDD. */
  public static int yyddd(LocalDate d) {
    return (d.getYear() % 100) * 1000 + d.getDayOfYear();
  }

  @Transactional
  public PurgeReport purge(PurgeParameters params) {
    int today = yyddd(clock.today());
    long sumRead = 0;
    long sumDel = 0;
    long dtlRead = 0;
    long dtlDel = 0;
    Long lastAcct = null;
    Optional<PendingAuthSummary> root = db.next(null);
    while (root.isPresent()) {
      PendingAuthSummary s = root.get();
      sumRead++;
      lastAcct = s.getAcctId();
      int approvedCnt = s.getApprovedAuthCnt();
      AuthKey after = null;
      Optional<PendingAuthDetail> child = db.nextChild(s.getAcctId(), null);
      while (child.isPresent()) {
        PendingAuthDetail d = child.get();
        dtlRead++;
        after = PendingAuthDatabase.keyOf(d);
        if (expired(d, today, params.expiryDays())) {
          if ("00".equals(d.getAuthRespCode())) {
            approvedCnt = (short) (approvedCnt - 1);
          }
          db.delete(d);
          dtlDel++;
        }
        child = db.nextChild(s.getAcctId(), after);
      }
      if (approvedCnt <= 0) {
        db.delete(s);
        sumDel++;
      }
      root = db.next(lastAcct);
    }
    return new PurgeReport(today, sumRead, sumDel, dtlRead, dtlDel);
  }

  static boolean expired(PendingAuthDetail d, int today, int expiryDays) {
    BigDecimal authDate = CobolMath.fit(BigDecimal.valueOf(99999L - d.getAuthDate9c()), 5, 0).abs();
    BigDecimal dayDiff = CobolMath.fit(BigDecimal.valueOf(today).subtract(authDate), 4, 0);
    return dayDiff.intValue() >= expiryDays;
  }
}
