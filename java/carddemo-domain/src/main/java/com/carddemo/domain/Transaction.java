package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** Transaction record RECLN 350. */
@Entity
@Table(
        name = "transaction_record",
        indexes = @Index(name = "idx_transaction_proc_ts", columnList = "tran_proc_ts"))
public class Transaction {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_id", length = 16, columnDefinition = "CHAR(16)")
    private String id;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_type_cd", length = 2, columnDefinition = "CHAR(2)", nullable = false)
    private String typeCode;
    @Column(name = "tran_cat_cd", nullable = false)
    private Integer categoryCode;
    @Column(name = "tran_source", length = 10, nullable = false)
    private String source;
    @Column(name = "tran_desc", length = 100, nullable = false)
    private String description;
    @Column(name = "tran_amt", precision = 11, scale = 2, nullable = false)
    private BigDecimal amount;
    @Column(name = "tran_merchant_id", nullable = false)
    private Long merchantId;
    @Column(name = "tran_merchant_name", length = 50, nullable = false)
    private String merchantName;
    @Column(name = "tran_merchant_city", length = 50, nullable = false)
    private String merchantCity;
    @Column(name = "tran_merchant_zip", length = 10, nullable = false)
    private String merchantZip;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_card_num", length = 16, columnDefinition = "CHAR(16)", nullable = false)
    private String cardNumber;
    @Column(name = "tran_orig_ts", length = 26, nullable = false)
    private String originalTimestamp;
    @Column(name = "tran_proc_ts", length = 26, nullable = false)
    private String processingTimestamp;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantCity() {
        return merchantCity;
    }

    public void setMerchantCity(String merchantCity) {
        this.merchantCity = merchantCity;
    }

    public String getMerchantZip() {
        return merchantZip;
    }

    public void setMerchantZip(String merchantZip) {
        this.merchantZip = merchantZip;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getOriginalTimestamp() {
        return originalTimestamp;
    }

    public void setOriginalTimestamp(String originalTimestamp) {
        this.originalTimestamp = originalTimestamp;
    }

    public String getProcessingTimestamp() {
        return processingTimestamp;
    }

    public void setProcessingTimestamp(String processingTimestamp) {
        this.processingTimestamp = processingTimestamp;
    }
}
