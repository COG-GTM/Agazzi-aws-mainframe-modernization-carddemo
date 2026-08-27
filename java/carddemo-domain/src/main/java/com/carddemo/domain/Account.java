package com.carddemo.domain;
import jakarta.persistence.*; import java.math.BigDecimal;
/** Account record RECLN 300; COBOL ACCT-EXPIRAION-DATE is misspelled in CVACT01Y. */
@Entity @Table(name="account")
public class Account {
 @Id @Column(name="acct_id") private Long acctId;
 @Column(name="acct_active_status",length=1,nullable=false) private String activeStatus;
 @Column(name="acct_curr_bal",precision=12,scale=2,nullable=false) private BigDecimal currentBalance;
 @Column(name="acct_credit_limit",precision=12,scale=2,nullable=false) private BigDecimal creditLimit;
 @Column(name="acct_cash_credit_limit",precision=12,scale=2,nullable=false) private BigDecimal cashCreditLimit;
 @Column(name="acct_open_date",length=10,nullable=false) private String openDate;
 @Column(name="acct_expiration_date",length=10,nullable=false) private String expirationDate;
 @Column(name="acct_reissue_date",length=10,nullable=false) private String reissueDate;
 @Column(name="acct_curr_cyc_credit",precision=12,scale=2,nullable=false) private BigDecimal currentCycleCredit;
 @Column(name="acct_curr_cyc_debit",precision=12,scale=2,nullable=false) private BigDecimal currentCycleDebit;
 @Column(name="acct_addr_zip",length=10,nullable=false) private String addressZip;
 @Column(name="acct_group_id",length=10,nullable=false) private String groupId;
 public Long getAcctId(){return acctId;} public void setAcctId(Long v){acctId=v;} public String getActiveStatus(){return activeStatus;} public void setActiveStatus(String v){activeStatus=v;}
 public BigDecimal getCurrentBalance(){return currentBalance;} public void setCurrentBalance(BigDecimal v){currentBalance=v;} public BigDecimal getCreditLimit(){return creditLimit;} public void setCreditLimit(BigDecimal v){creditLimit=v;} public BigDecimal getCashCreditLimit(){return cashCreditLimit;} public void setCashCreditLimit(BigDecimal v){cashCreditLimit=v;}
 public String getOpenDate(){return openDate;} public void setOpenDate(String v){openDate=v;} public String getExpirationDate(){return expirationDate;} public void setExpirationDate(String v){expirationDate=v;} public String getReissueDate(){return reissueDate;} public void setReissueDate(String v){reissueDate=v;}
 public BigDecimal getCurrentCycleCredit(){return currentCycleCredit;} public void setCurrentCycleCredit(BigDecimal v){currentCycleCredit=v;} public BigDecimal getCurrentCycleDebit(){return currentCycleDebit;} public void setCurrentCycleDebit(BigDecimal v){currentCycleDebit=v;} public String getAddressZip(){return addressZip;} public void setAddressZip(String v){addressZip=v;} public String getGroupId(){return groupId;} public void setGroupId(String v){groupId=v;}
}
