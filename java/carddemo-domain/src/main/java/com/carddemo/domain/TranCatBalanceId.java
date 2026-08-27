package com.carddemo.domain;
import jakarta.persistence.*; import java.io.Serializable;
@Embeddable public class TranCatBalanceId implements Serializable {
 @Column(name="trancat_acct_id") private Long accountId; @Column(name="trancat_type_cd",length=2) private String typeCode; @Column(name="trancat_cd") private Integer categoryCode;
 public TranCatBalanceId(){} public TranCatBalanceId(Long a,String t,Integer c){accountId=a;typeCode=t;categoryCode=c;} public Long getAccountId(){return accountId;} public void setAccountId(Long v){accountId=v;} public String getTypeCode(){return typeCode;} public void setTypeCode(String v){typeCode=v;} public Integer getCategoryCode(){return categoryCode;} public void setCategoryCode(Integer v){categoryCode=v;}
 @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof TranCatBalanceId x))return false;return java.util.Objects.equals(accountId,x.accountId)&&java.util.Objects.equals(typeCode,x.typeCode)&&java.util.Objects.equals(categoryCode,x.categoryCode);} @Override public int hashCode(){return java.util.Objects.hash(accountId,typeCode,categoryCode);}
}
