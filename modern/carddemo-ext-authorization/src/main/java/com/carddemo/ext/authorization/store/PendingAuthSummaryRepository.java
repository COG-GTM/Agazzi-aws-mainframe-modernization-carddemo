package com.carddemo.ext.authorization.store;

import com.carddemo.ext.authorization.model.PendingAuthSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Root segment PAUTSUM0 access (HIDAM primary index on ACCNTID). */
public interface PendingAuthSummaryRepository extends JpaRepository<PendingAuthSummary, Long> {

  Optional<PendingAuthSummary> findFirstByOrderByAcctIdAsc();

  Optional<PendingAuthSummary> findFirstByAcctIdGreaterThanOrderByAcctIdAsc(Long acctId);
}
