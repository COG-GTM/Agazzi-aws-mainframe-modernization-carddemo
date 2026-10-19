package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for a transaction-category balance. */
@Embeddable
public class TranCatBalanceId implements Serializable {

    @Column(name = "trancat_acct_id")
    private Long accountId;
    @Column(name = "trancat_type_cd", length = 2)
    private String typeCode;
    @Column(name = "trancat_cd")
    private Integer categoryCode;

    public TranCatBalanceId() {
    }

    public TranCatBalanceId(Long accountId, String typeCode, Integer categoryCode) {
        this.accountId = accountId;
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public Integer getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(Integer categoryCode) {
        this.categoryCode = categoryCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TranCatBalanceId that)) {
            return false;
        }
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(typeCode, that.typeCode)
                && Objects.equals(categoryCode, that.categoryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, typeCode, categoryCode);
    }
}
