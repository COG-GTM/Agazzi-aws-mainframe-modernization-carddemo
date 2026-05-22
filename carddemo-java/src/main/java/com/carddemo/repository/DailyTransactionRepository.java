package com.carddemo.repository;

import com.carddemo.entity.DailyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DailyTransactionRepository extends JpaRepository<DailyTransaction, Long> {
    List<DailyTransaction> findByPostedFalse();
    long countByPostedFalse();
}
