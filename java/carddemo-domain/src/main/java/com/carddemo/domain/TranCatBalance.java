package com.carddemo.domain;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="tran_cat_balance") public class TranCatBalance {
 @EmbeddedId private TranCatBalanceId id; @Column(name="tran_cat_bal",precision=11,scale=2,nullable=false) private BigDecimal balance;
 public TranCatBalanceId getId(){return id;} public void setId(TranCatBalanceId v){id=v;} public BigDecimal getBalance(){return balance;} public void setBalance(BigDecimal v){balance=v;}
}
