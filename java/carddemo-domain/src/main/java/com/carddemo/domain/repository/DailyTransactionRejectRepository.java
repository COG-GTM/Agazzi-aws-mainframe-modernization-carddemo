package com.carddemo.domain.repository;

import com.carddemo.domain.DailyTransactionReject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTransactionRejectRepository extends JpaRepository<DailyTransactionReject, Long> {
}
