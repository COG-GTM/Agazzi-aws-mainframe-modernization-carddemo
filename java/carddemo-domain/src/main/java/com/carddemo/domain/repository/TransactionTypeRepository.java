package com.carddemo.domain.repository;

import com.carddemo.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {
}
