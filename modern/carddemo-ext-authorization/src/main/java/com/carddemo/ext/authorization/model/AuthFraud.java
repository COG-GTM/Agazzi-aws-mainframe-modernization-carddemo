package com.carddemo.ext.authorization.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DB2 table CARDDEMO.AUTHFRDS (fraud reports written by COPAUS2C). */
@Entity
@Table(name = "auth_fraud")
@IdClass(AuthFraudId.class)
public class AuthFraud {

  @Id
  @Column(name = "card_num", nullable = false, length = 16)
  private String cardNum;

  @Id
  @Column(name = "auth_ts", nullable = false)
  private LocalDateTime authTs;

  @Column(name = "auth_type", length = 4)
  private String authType;

  @Column(name = "card_expiry_date", length = 4)
  private String cardExpiryDate;

  @Column(name = "message_type", length = 6)
  private String messageType;

  @Column(name = "message_source", length = 6)
  private String messageSource;

  @Column(name = "auth_id_code", length = 6)
  private String authIdCode;

  @Column(name = "auth_resp_code", length = 2)
  private String authRespCode;

  @Column(name = "auth_resp_reason", length = 4)
  private String authRespReason;

  @Column(name = "processing_code", length = 6)
  private String processingCode;

  @Column(name = "transaction_amt", precision = 12, scale = 2)
  private BigDecimal transactionAmt;

  @Column(name = "approved_amt", precision = 12, scale = 2)
  private BigDecimal approvedAmt;

  @Column(name = "merchant_category_code", length = 4)
  private String merchantCategoryCode;

  @Column(name = "acqr_country_code", length = 3)
  private String acqrCountryCode;

  @Column(name = "pos_entry_mode")
  private Short posEntryMode;

  @Column(name = "merchant_id", length = 15)
  private String merchantId;

  @Column(name = "merchant_name", length = 22)
  private String merchantName;

  @Column(name = "merchant_city", length = 13)
  private String merchantCity;

  @Column(name = "merchant_state", length = 2)
  private String merchantState;

  @Column(name = "merchant_zip", length = 9)
  private String merchantZip;

  @Column(name = "transaction_id", length = 15)
  private String transactionId;

  @Column(name = "match_status", length = 1)
  private String matchStatus;

  @Column(name = "auth_fraud", length = 1)
  private String authFraud;

  @Column(name = "fraud_rpt_date")
  private LocalDate fraudRptDate;

  @Column(name = "acct_id", precision = 11, scale = 0)
  private BigDecimal acctId;

  @Column(name = "cust_id", precision = 9, scale = 0)
  private BigDecimal custId;

  public String getCardNum() {
    return cardNum;
  }

  public void setCardNum(String cardNum) {
    this.cardNum = cardNum;
  }

  public LocalDateTime getAuthTs() {
    return authTs;
  }

  public void setAuthTs(LocalDateTime authTs) {
    this.authTs = authTs;
  }

  public String getAuthType() {
    return authType;
  }

  public void setAuthType(String authType) {
    this.authType = authType;
  }

  public String getCardExpiryDate() {
    return cardExpiryDate;
  }

  public void setCardExpiryDate(String cardExpiryDate) {
    this.cardExpiryDate = cardExpiryDate;
  }

  public String getMessageType() {
    return messageType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
  }

  public String getMessageSource() {
    return messageSource;
  }

  public void setMessageSource(String messageSource) {
    this.messageSource = messageSource;
  }

  public String getAuthIdCode() {
    return authIdCode;
  }

  public void setAuthIdCode(String authIdCode) {
    this.authIdCode = authIdCode;
  }

  public String getAuthRespCode() {
    return authRespCode;
  }

  public void setAuthRespCode(String authRespCode) {
    this.authRespCode = authRespCode;
  }

  public String getAuthRespReason() {
    return authRespReason;
  }

  public void setAuthRespReason(String authRespReason) {
    this.authRespReason = authRespReason;
  }

  public String getProcessingCode() {
    return processingCode;
  }

  public void setProcessingCode(String processingCode) {
    this.processingCode = processingCode;
  }

  public BigDecimal getTransactionAmt() {
    return transactionAmt;
  }

  public void setTransactionAmt(BigDecimal transactionAmt) {
    this.transactionAmt = transactionAmt;
  }

  public BigDecimal getApprovedAmt() {
    return approvedAmt;
  }

  public void setApprovedAmt(BigDecimal approvedAmt) {
    this.approvedAmt = approvedAmt;
  }

  public String getMerchantCategoryCode() {
    return merchantCategoryCode;
  }

  public void setMerchantCategoryCode(String merchantCategoryCode) {
    this.merchantCategoryCode = merchantCategoryCode;
  }

  public String getAcqrCountryCode() {
    return acqrCountryCode;
  }

  public void setAcqrCountryCode(String acqrCountryCode) {
    this.acqrCountryCode = acqrCountryCode;
  }

  public Short getPosEntryMode() {
    return posEntryMode;
  }

  public void setPosEntryMode(Short posEntryMode) {
    this.posEntryMode = posEntryMode;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String merchantId) {
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

  public String getMerchantState() {
    return merchantState;
  }

  public void setMerchantState(String merchantState) {
    this.merchantState = merchantState;
  }

  public String getMerchantZip() {
    return merchantZip;
  }

  public void setMerchantZip(String merchantZip) {
    this.merchantZip = merchantZip;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public String getMatchStatus() {
    return matchStatus;
  }

  public void setMatchStatus(String matchStatus) {
    this.matchStatus = matchStatus;
  }

  public String getAuthFraud() {
    return authFraud;
  }

  public void setAuthFraud(String authFraud) {
    this.authFraud = authFraud;
  }

  public LocalDate getFraudRptDate() {
    return fraudRptDate;
  }

  public void setFraudRptDate(LocalDate fraudRptDate) {
    this.fraudRptDate = fraudRptDate;
  }

  public BigDecimal getAcctId() {
    return acctId;
  }

  public void setAcctId(BigDecimal acctId) {
    this.acctId = acctId;
  }

  public BigDecimal getCustId() {
    return custId;
  }

  public void setCustId(BigDecimal custId) {
    this.custId = custId;
  }
}
