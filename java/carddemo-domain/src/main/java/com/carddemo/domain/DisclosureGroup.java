package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Disclosure-group record RECLN 50. */
@Entity
@Table(name = "disclosure_group")
public class DisclosureGroup {

    @EmbeddedId
    private DisclosureGroupId id;

    @Column(name = "dis_int_rate", precision = 6, scale = 2, nullable = false)
    private BigDecimal interestRate;

    public DisclosureGroupId getId() {
        return id;
    }

    public void setId(DisclosureGroupId id) {
        this.id = id;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }
}
