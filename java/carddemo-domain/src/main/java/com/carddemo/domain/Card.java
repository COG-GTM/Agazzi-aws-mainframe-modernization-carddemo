package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Card record RECLN 150. */
@Entity
@Table(name = "card")
public class Card {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_num", length = 16, columnDefinition = "CHAR(16)")
    private String cardNumber;
    @Column(name = "card_acct_id", nullable = false)
    private Long accountId;
    @Column(name = "card_cvv_cd", nullable = false)
    private Integer cvvCode;
    @Column(name = "card_embossed_name", length = 50, nullable = false)
    private String embossedName;
    @Column(name = "card_expiration_date", length = 10, nullable = false)
    private String expirationDate;
    @Column(name = "card_active_status", length = 1, nullable = false)
    private String activeStatus;

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Integer getCvvCode() {
        return cvvCode;
    }

    public void setCvvCode(Integer cvvCode) {
        this.cvvCode = cvvCode;
    }

    public String getEmbossedName() {
        return embossedName;
    }

    public void setEmbossedName(String embossedName) {
        this.embossedName = embossedName;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(String activeStatus) {
        this.activeStatus = activeStatus;
    }
}
