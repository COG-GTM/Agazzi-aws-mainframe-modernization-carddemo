package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Transaction-category record RECLN 60. */
@Entity
@Table(name = "transaction_category")
public class TransactionCategory {

    @EmbeddedId
    private TransactionCategoryId id;

    @Column(name = "tran_cat_type_desc", length = 50, nullable = false)
    private String description;

    public TransactionCategoryId getId() {
        return id;
    }

    public void setId(TransactionCategoryId id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
