package com.carddemo.ext.authorization.store;

import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.model.PendingAuthDetailId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Child segment PAUTDTL1 access in PAUT9CTS order (ascending inverted timestamp). */
public interface PendingAuthDetailRepository
    extends JpaRepository<PendingAuthDetail, PendingAuthDetailId> {

  List<PendingAuthDetail> findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc(Long acctId);

  @Query(
      "select d from PendingAuthDetail d where d.acctId = :acct and (d.authDate9c > :date"
          + " or (d.authDate9c = :date and d.authTime9c > :time))"
          + " order by d.authDate9c asc, d.authTime9c asc")
  List<PendingAuthDetail> findAfter(
      @Param("acct") Long acctId,
      @Param("date") Integer authDate9c,
      @Param("time") Long authTime9c,
      Pageable page);

  @Query(
      "select d from PendingAuthDetail d where d.acctId = :acct and (d.authDate9c > :date"
          + " or (d.authDate9c = :date and d.authTime9c >= :time))"
          + " order by d.authDate9c asc, d.authTime9c asc")
  List<PendingAuthDetail> findFrom(
      @Param("acct") Long acctId,
      @Param("date") Integer authDate9c,
      @Param("time") Long authTime9c,
      Pageable page);

  default Optional<PendingAuthDetail> firstAfter(Long acctId, Integer date, Long time) {
    return findAfter(acctId, date, time, Pageable.ofSize(1)).stream().findFirst();
  }

  default Optional<PendingAuthDetail> firstFrom(Long acctId, Integer date, Long time) {
    return findFrom(acctId, date, time, Pageable.ofSize(1)).stream().findFirst();
  }

  long countByAcctId(Long acctId);
}
