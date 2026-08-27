package com.carddemo.domain;
import jakarta.persistence.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
/** Card cross-reference record RECLN 50; source ASCII export is truncated to 36 columns. */
@Entity @Table(name="card_xref",indexes=@Index(name="idx_card_xref_acct",columnList="xref_acct_id"))
public class CardXref {
 @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(name="xref_card_num",length=16,columnDefinition="CHAR(16)") private String cardNumber; @Column(name="xref_cust_id",nullable=false) private Long customerId; @Column(name="xref_acct_id",nullable=false) private Long accountId;
 public String getCardNumber(){return cardNumber;} public void setCardNumber(String v){cardNumber=v;} public Long getCustomerId(){return customerId;} public void setCustomerId(Long v){customerId=v;} public Long getAccountId(){return accountId;} public void setAccountId(Long v){accountId=v;}
}
