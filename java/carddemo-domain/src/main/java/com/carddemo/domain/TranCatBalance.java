package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Transaction-category balance record RECLN 50. */
@Entity
@Table(name = "tran_cat_balance")
public class TranCatBalance {

    @EmbeddedId
    private TranCatBalanceId id;

    @Column(name = "tran_cat_bal", precision = 11, scale = 2, nullable = false)
    private BigDecimal balance;

    public TranCatBalanceId getId() {
        return id;
    }

    public void setId(TranCatBalanceId id) {
        this.id = id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
