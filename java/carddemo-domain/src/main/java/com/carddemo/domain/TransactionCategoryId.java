package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for a transaction category. */
@Embeddable
public class TransactionCategoryId implements Serializable {

    @Column(name = "tran_type_cd", length = 2)
    private String typeCode;
    @Column(name = "tran_cat_cd")
    private Integer categoryCode;

    public TransactionCategoryId() {
    }

    public TransactionCategoryId(String typeCode, Integer categoryCode) {
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
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
        if (!(other instanceof TransactionCategoryId that)) {
            return false;
        }
        return Objects.equals(typeCode, that.typeCode)
                && Objects.equals(categoryCode, that.categoryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeCode, categoryCode);
    }
}
