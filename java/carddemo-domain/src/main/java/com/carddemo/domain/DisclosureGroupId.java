package com.carddemo.domain;
import jakarta.persistence.*; import java.io.Serializable;
@Embeddable public class DisclosureGroupId implements Serializable {
 @Column(name="dis_acct_group_id",length=10) private String groupId; @Column(name="dis_tran_type_cd",length=2) private String typeCode; @Column(name="dis_tran_cat_cd") private Integer categoryCode;
 public DisclosureGroupId(){} public DisclosureGroupId(String g,String t,Integer c){groupId=g;typeCode=t;categoryCode=c;} public String getGroupId(){return groupId;} public void setGroupId(String v){groupId=v;} public String getTypeCode(){return typeCode;} public void setTypeCode(String v){typeCode=v;} public Integer getCategoryCode(){return categoryCode;} public void setCategoryCode(Integer v){categoryCode=v;}
 @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof DisclosureGroupId x))return false;return java.util.Objects.equals(groupId,x.groupId)&&java.util.Objects.equals(typeCode,x.typeCode)&&java.util.Objects.equals(categoryCode,x.categoryCode);} @Override public int hashCode(){return java.util.Objects.hash(groupId,typeCode,categoryCode);}
}
