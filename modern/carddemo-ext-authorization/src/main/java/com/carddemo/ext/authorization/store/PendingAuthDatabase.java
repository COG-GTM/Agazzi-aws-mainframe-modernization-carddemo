package com.carddemo.ext.authorization.store;

import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.model.PendingAuthDetailId;
import com.carddemo.ext.authorization.model.PendingAuthSummary;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * DL/I facade over DBPAUTP0 (PSBPAUTB): {@code findFirst}=GU, {@code next}=GN/GNP, {@code
 * insert}=ISRT, {@code replace}=REPL, {@code delete}=DLET. Status codes are mapped to {@link
 * Optional#empty()} (GE/GB) and {@link DuplicateSegmentException} (II).
 */
@Component
@ConditionalOnAuthorizationExtension
public class PendingAuthDatabase {

  private final PendingAuthSummaryRepository summaries;
  private final PendingAuthDetailRepository details;

  public PendingAuthDatabase(
      PendingAuthSummaryRepository summaries, PendingAuthDetailRepository details) {
    this.summaries = summaries;
    this.details = details;
  }

  /** Thrown on ISRT of an existing key (DL/I status II). */
  public static class DuplicateSegmentException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DuplicateSegmentException(String message) {
      super(message);
    }
  }

  /** GU PAUTSUM0 WHERE ACCNTID = acctId. */
  public Optional<PendingAuthSummary> findFirst(long acctId) {
    return summaries.findById(acctId);
  }

  /** GN PAUTSUM0 (unqualified) after the given root; {@code null} starts from the top. */
  public Optional<PendingAuthSummary> next(Long afterAcctId) {
    return afterAcctId == null
        ? summaries.findFirstByOrderByAcctIdAsc()
        : summaries.findFirstByAcctIdGreaterThanOrderByAcctIdAsc(afterAcctId);
  }

  /** GU/GNP PAUTDTL1 WHERE PAUT9CTS = key under the given root. */
  public Optional<PendingAuthDetail> findFirstChild(long acctId, AuthKey key) {
    return details.findById(new PendingAuthDetailId(acctId, key.authDate9c(), key.authTime9c()));
  }

  /** GNP PAUTDTL1 (unqualified) after {@code after}; {@code null} = first child. */
  public Optional<PendingAuthDetail> nextChild(long acctId, AuthKey after) {
    return after == null
        ? details.firstFrom(acctId, Integer.MIN_VALUE, Long.MIN_VALUE)
        : details.firstAfter(acctId, after.authDate9c(), after.authTime9c());
  }

  public PendingAuthSummary insert(PendingAuthSummary root) {
    if (summaries.existsById(root.getAcctId())) {
      throw new DuplicateSegmentException("PAUTSUM0 " + root.getAcctId());
    }
    return summaries.save(root);
  }

  public PendingAuthDetail insert(PendingAuthDetail child) {
    PendingAuthDetailId id =
        new PendingAuthDetailId(child.getAcctId(), child.getAuthDate9c(), child.getAuthTime9c());
    if (details.existsById(id)) {
      throw new DuplicateSegmentException("PAUTDTL1 " + id);
    }
    return details.save(child);
  }

  public PendingAuthSummary replace(PendingAuthSummary root) {
    return summaries.save(root);
  }

  public PendingAuthDetail replace(PendingAuthDetail child) {
    return details.save(child);
  }

  /** DLET PAUTSUM0 cascades to its dependents, as in IMS. */
  public void delete(PendingAuthSummary root) {
    details.deleteAll(details.findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc(root.getAcctId()));
    details.flush();
    summaries.delete(root);
  }

  public void delete(PendingAuthDetail child) {
    details.delete(child);
  }

  public static AuthKey keyOf(PendingAuthDetail d) {
    return new AuthKey(d.getAuthDate9c(), d.getAuthTime9c());
  }
}
