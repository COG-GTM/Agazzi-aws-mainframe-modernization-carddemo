package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * DALYREJS persistence record. The original sequential record is 350 bytes
 * of transaction data followed by an 80-byte validation trailer.
 */
@Entity
@Table(name = "daily_transaction_reject")
public class DailyTransactionReject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reject_id")
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "raw_record", length = 350, columnDefinition = "CHAR(350)", nullable = false)
    private String rawRecord;

    @Column(name = "reject_reason_code", nullable = false)
    private Integer reasonCode;

    @Column(name = "reason_description", length = 76, nullable = false)
    private String reasonDescription;

    @Column(name = "rejected_at", nullable = false)
    private LocalDateTime rejectedAt;

    public Long getId() {
        return id;
    }

    public String getRawRecord() {
        return rawRecord;
    }

    public void setRawRecord(String rawRecord) {
        this.rawRecord = rawRecord;
    }

    public Integer getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(Integer reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonDescription() {
        return reasonDescription;
    }

    public void setReasonDescription(String reasonDescription) {
        this.reasonDescription = reasonDescription;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }
}
