package com.carddemo.domain;
import jakarta.persistence.*; import java.io.Serializable;
@Embeddable public class TransactionCategoryId implements Serializable {
 @Column(name="tran_type_cd",length=2) private String typeCode; @Column(name="tran_cat_cd") private Integer categoryCode;
 public TransactionCategoryId(){} public TransactionCategoryId(String t,Integer c){typeCode=t;categoryCode=c;} public String getTypeCode(){return typeCode;} public void setTypeCode(String v){typeCode=v;} public Integer getCategoryCode(){return categoryCode;} public void setCategoryCode(Integer v){categoryCode=v;}
 @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof TransactionCategoryId x))return false;return java.util.Objects.equals(typeCode,x.typeCode)&&java.util.Objects.equals(categoryCode,x.categoryCode);} @Override public int hashCode(){return java.util.Objects.hash(typeCode,categoryCode);}
}
