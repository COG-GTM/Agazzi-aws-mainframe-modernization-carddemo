package com.carddemo.domain.repository;

import com.carddemo.domain.TranCatBalance;
import com.carddemo.domain.TranCatBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranCatBalanceRepository extends JpaRepository<TranCatBalance, TranCatBalanceId> {
}
