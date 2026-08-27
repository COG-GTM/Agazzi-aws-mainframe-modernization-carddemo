package com.carddemo.domain;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="disclosure_group") public class DisclosureGroup {
 @EmbeddedId private DisclosureGroupId id; @Column(name="dis_int_rate",precision=6,scale=2,nullable=false) private BigDecimal interestRate;
 public DisclosureGroupId getId(){return id;} public void setId(DisclosureGroupId v){id=v;} public BigDecimal getInterestRate(){return interestRate;} public void setInterestRate(BigDecimal v){interestRate=v;}
}
