package com.carddemo.ext.authorization.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

/** IMS root segment PAUTSUM0 (copybook CIPAUSMY) of DBPAUTP0. */
@Entity
@Table(name = "pending_auth_summary")
public class PendingAuthSummary {

  public static final int FILLER_BYTES = 34;

  @Id
  @Column(name = "acct_id", nullable = false)
  private Long acctId;

  @Column(name = "cust_id", nullable = false)
  private Integer custId;

  @Column(name = "auth_status", nullable = false, length = 1)
  private String authStatus = " ";

  @Column(name = "account_status", nullable = false, length = 10)
  private String accountStatus = " ".repeat(10);

  @Column(name = "credit_limit", nullable = false, precision = 11, scale = 2)
  private BigDecimal creditLimit = BigDecimal.ZERO.setScale(2);

  @Column(name = "cash_limit", nullable = false, precision = 11, scale = 2)
  private BigDecimal cashLimit = BigDecimal.ZERO.setScale(2);

  @Column(name = "credit_balance", nullable = false, precision = 11, scale = 2)
  private BigDecimal creditBalance = BigDecimal.ZERO.setScale(2);

  @Column(name = "cash_balance", nullable = false, precision = 11, scale = 2)
  private BigDecimal cashBalance = BigDecimal.ZERO.setScale(2);

  @Column(name = "approved_auth_cnt", nullable = false)
  private short approvedAuthCnt;

  @Column(name = "declined_auth_cnt", nullable = false)
  private short declinedAuthCnt;

  @Column(name = "approved_auth_amt", nullable = false, precision = 11, scale = 2)
  private BigDecimal approvedAuthAmt = BigDecimal.ZERO.setScale(2);

  @Column(name = "declined_auth_amt", nullable = false, precision = 11, scale = 2)
  private BigDecimal declinedAuthAmt = BigDecimal.ZERO.setScale(2);

  /** Raw FILLER X(34) as hex (may hold low-values). */
  @Column(name = "filler", nullable = false, length = 68)
  private String filler = "20".repeat(FILLER_BYTES);

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public Long getAcctId() {
    return acctId;
  }

  public void setAcctId(Long acctId) {
    this.acctId = acctId;
  }

  public Integer getCustId() {
    return custId;
  }

  public void setCustId(Integer custId) {
    this.custId = custId;
  }

  public String getAuthStatus() {
    return authStatus;
  }

  public void setAuthStatus(String authStatus) {
    this.authStatus = authStatus;
  }

  public String getAccountStatus() {
    return accountStatus;
  }

  public void setAccountStatus(String accountStatus) {
    this.accountStatus = accountStatus;
  }

  public BigDecimal getCreditLimit() {
    return creditLimit;
  }

  public void setCreditLimit(BigDecimal creditLimit) {
    this.creditLimit = creditLimit;
  }

  public BigDecimal getCashLimit() {
    return cashLimit;
  }

  public void setCashLimit(BigDecimal cashLimit) {
    this.cashLimit = cashLimit;
  }

  public BigDecimal getCreditBalance() {
    return creditBalance;
  }

  public void setCreditBalance(BigDecimal creditBalance) {
    this.creditBalance = creditBalance;
  }

  public BigDecimal getCashBalance() {
    return cashBalance;
  }

  public void setCashBalance(BigDecimal cashBalance) {
    this.cashBalance = cashBalance;
  }

  public short getApprovedAuthCnt() {
    return approvedAuthCnt;
  }

  public void setApprovedAuthCnt(short approvedAuthCnt) {
    this.approvedAuthCnt = approvedAuthCnt;
  }

  public short getDeclinedAuthCnt() {
    return declinedAuthCnt;
  }

  public void setDeclinedAuthCnt(short declinedAuthCnt) {
    this.declinedAuthCnt = declinedAuthCnt;
  }

  public BigDecimal getApprovedAuthAmt() {
    return approvedAuthAmt;
  }

  public void setApprovedAuthAmt(BigDecimal approvedAuthAmt) {
    this.approvedAuthAmt = approvedAuthAmt;
  }

  public BigDecimal getDeclinedAuthAmt() {
    return declinedAuthAmt;
  }

  public void setDeclinedAuthAmt(BigDecimal declinedAuthAmt) {
    this.declinedAuthAmt = declinedAuthAmt;
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
