package com.carddemo.entity;

import java.io.Serializable;
import java.util.Objects;

public class TransactionCategoryId implements Serializable {

    private String tranTypeCd;
    private Integer tranCatCd;

    public TransactionCategoryId() {}

    public TransactionCategoryId(String tranTypeCd, Integer tranCatCd) {
        this.tranTypeCd = tranTypeCd;
        this.tranCatCd = tranCatCd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionCategoryId that)) return false;
        return Objects.equals(tranTypeCd, that.tranTypeCd) && Objects.equals(tranCatCd, that.tranCatCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranTypeCd, tranCatCd);
    }
}
