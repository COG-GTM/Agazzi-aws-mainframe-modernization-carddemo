package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Transaction type record RECLN 60. */
@Entity
@Table(name = "transaction_type")
public class TransactionType {

    @Id
    @Column(name = "tran_type", length = 2)
    private String type;
    @Column(name = "tran_type_desc", length = 50, nullable = false)
    private String description;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
