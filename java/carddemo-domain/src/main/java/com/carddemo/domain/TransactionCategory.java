package com.carddemo.domain;
import jakarta.persistence.*;
@Entity @Table(name="transaction_category") public class TransactionCategory {
 @EmbeddedId private TransactionCategoryId id; @Column(name="tran_cat_type_desc",length=50,nullable=false) private String description;
 public TransactionCategoryId getId(){return id;} public void setId(TransactionCategoryId v){id=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
}
