package com.carddemo.domain;
import jakarta.persistence.*;
@Entity @Table(name="transaction_type") public class TransactionType {
 @Id @Column(name="tran_type",length=2) private String type; @Column(name="tran_type_desc",length=50,nullable=false) private String description;
 public String getType(){return type;} public void setType(String v){type=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
}
