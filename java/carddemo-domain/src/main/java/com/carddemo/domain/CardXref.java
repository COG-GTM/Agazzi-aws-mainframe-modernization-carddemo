package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Card cross-reference record RECLN 50; source ASCII export is truncated to 36 columns. */
@Entity
@Table(name = "card_xref", indexes = @Index(name = "idx_card_xref_acct", columnList = "xref_acct_id"))
public class CardXref {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "xref_card_num", length = 16, columnDefinition = "CHAR(16)")
    private String cardNumber;
    @Column(name = "xref_cust_id", nullable = false)
    private Long customerId;
    @Column(name = "xref_acct_id", nullable = false)
    private Long accountId;

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
}
