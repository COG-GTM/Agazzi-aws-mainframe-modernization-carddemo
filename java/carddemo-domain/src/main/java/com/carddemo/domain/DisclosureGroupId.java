package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for a disclosure group. */
@Embeddable
public class DisclosureGroupId implements Serializable {

    @Column(name = "dis_acct_group_id", length = 10)
    private String groupId;
    @Column(name = "dis_tran_type_cd", length = 2)
    private String typeCode;
    @Column(name = "dis_tran_cat_cd")
    private Integer categoryCode;

    public DisclosureGroupId() {
    }

    public DisclosureGroupId(String groupId, String typeCode, Integer categoryCode) {
        this.groupId = groupId;
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
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
        if (!(other instanceof DisclosureGroupId that)) {
            return false;
        }
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(typeCode, that.typeCode)
                && Objects.equals(categoryCode, that.categoryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, typeCode, categoryCode);
    }
}
