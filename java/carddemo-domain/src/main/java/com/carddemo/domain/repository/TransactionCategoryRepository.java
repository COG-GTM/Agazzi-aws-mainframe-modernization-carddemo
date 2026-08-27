package com.carddemo.domain.repository;

import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCategoryRepository
        extends JpaRepository<TransactionCategory, TransactionCategoryId> {
}
