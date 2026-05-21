package com.carddemo.repository;

import com.carddemo.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    Page<Transaction> findByCardNum(String cardNum, Pageable pageable);

    List<Transaction> findByCardNumAndOrigTsBetween(
            String cardNum, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM Transaction t WHERE t.cardNum IN :cardNums ORDER BY t.origTs DESC")
    Page<Transaction> findByCardNumIn(@Param("cardNums") List<String> cardNums, Pageable pageable);
}
