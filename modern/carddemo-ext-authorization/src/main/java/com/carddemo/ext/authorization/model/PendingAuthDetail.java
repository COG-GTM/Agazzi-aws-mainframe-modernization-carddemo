package com.carddemo.ext.authorization.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

/** IMS child segment PAUTDTL1 (copybook CIPAUDTY); key PAUT9CTS = (auth_date_9c, auth_time_9c). */
@Entity
@Table(name = "pending_auth_detail")
@IdClass(PendingAuthDetailId.class)
public class PendingAuthDetail {

  @Id
  @Column(name = "acct_id", nullable = false)
  private Long acctId;

  @Id
  @Column(name = "auth_date_9c", nullable = false)
  private Integer authDate9c;

  @Id
  @Column(name = "auth_time_9c", nullable = false)
  private Long authTime9c;

  @Column(name = "auth_orig_date", nullable = false, length = 6)
  private String authOrigDate = " ".repeat(6);

  @Column(name = "auth_orig_time", nullable = false, length = 6)
  private String authOrigTime = " ".repeat(6);

  @Column(name = "card_num", nullable = false, length = 16)
  private String cardNum = " ".repeat(16);

  @Column(name = "auth_type", nullable = false, length = 4)
  private String authType = " ".repeat(4);

  @Column(name = "card_expiry_date", nullable = false, length = 4)
  private String cardExpiryDate = " ".repeat(4);

  @Column(name = "message_type", nullable = false, length = 6)
  private String messageType = " ".repeat(6);

  @Column(name = "message_source", nullable = false, length = 6)
  private String messageSource = " ".repeat(6);

  @Column(name = "auth_id_code", nullable = false, length = 6)
  private String authIdCode = " ".repeat(6);

  @Column(name = "auth_resp_code", nullable = false, length = 2)
  private String authRespCode = " ".repeat(2);

  @Column(name = "auth_resp_reason", nullable = false, length = 4)
  private String authRespReason = " ".repeat(4);

  @Column(name = "processing_code", nullable = false)
  private Integer processingCode = 0;

  @Column(name = "transaction_amt", nullable = false, precision = 12, scale = 2)
  private BigDecimal transactionAmt = BigDecimal.ZERO.setScale(2);

  @Column(name = "approved_amt", nullable = false, precision = 12, scale = 2)
  private BigDecimal approvedAmt = BigDecimal.ZERO.setScale(2);

  @Column(name = "merchant_category_code", nullable = false, length = 4)
  private String merchantCategoryCode = " ".repeat(4);

  @Column(name = "acqr_country_code", nullable = false, length = 3)
  private String acqrCountryCode = " ".repeat(3);

  @Column(name = "pos_entry_mode", nullable = false)
  private Short posEntryMode = (short) 0;

  @Column(name = "merchant_id", nullable = false, length = 15)
  private String merchantId = " ".repeat(15);

  @Column(name = "merchant_name", nullable = false, length = 22)
  private String merchantName = " ".repeat(22);

  @Column(name = "merchant_city", nullable = false, length = 13)
  private String merchantCity = " ".repeat(13);

  @Column(name = "merchant_state", nullable = false, length = 2)
  private String merchantState = " ".repeat(2);

  @Column(name = "merchant_zip", nullable = false, length = 9)
  private String merchantZip = " ".repeat(9);

  @Column(name = "transaction_id", nullable = false, length = 15)
  private String transactionId = " ".repeat(15);

  @Column(name = "match_status", nullable = false, length = 1)
  private String matchStatus = " ";

  @Column(name = "auth_fraud", nullable = false, length = 1)
  private String authFraud = " ";

  @Column(name = "fraud_rpt_date", nullable = false, length = 8)
  private String fraudRptDate = " ".repeat(8);

  @Column(name = "filler", nullable = false, length = 34)
  private String filler = "20".repeat(17);

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public Long getAcctId() {
    return acctId;
  }

  public void setAcctId(Long acctId) {
    this.acctId = acctId;
  }

  public Integer getAuthDate9c() {
    return authDate9c;
  }

  public void setAuthDate9c(Integer authDate9c) {
    this.authDate9c = authDate9c;
  }

  public Long getAuthTime9c() {
    return authTime9c;
  }

  public void setAuthTime9c(Long authTime9c) {
    this.authTime9c = authTime9c;
  }

  public String getAuthOrigDate() {
    return authOrigDate;
  }

  public void setAuthOrigDate(String authOrigDate) {
    this.authOrigDate = authOrigDate;
  }

  public String getAuthOrigTime() {
    return authOrigTime;
  }

  public void setAuthOrigTime(String authOrigTime) {
    this.authOrigTime = authOrigTime;
  }

  public String getCardNum() {
    return cardNum;
  }

  public void setCardNum(String cardNum) {
    this.cardNum = cardNum;
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

  public Integer getProcessingCode() {
    return processingCode;
  }

  public void setProcessingCode(Integer processingCode) {
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

  public String getFraudRptDate() {
    return fraudRptDate;
  }

  public void setFraudRptDate(String fraudRptDate) {
    this.fraudRptDate = fraudRptDate;
  }

  public String getFiller() {
    return filler;
  }

  public void setFiller(String filler) {
    this.filler = filler;
  }

  public long getVersion() {
    return version;
  }
}
