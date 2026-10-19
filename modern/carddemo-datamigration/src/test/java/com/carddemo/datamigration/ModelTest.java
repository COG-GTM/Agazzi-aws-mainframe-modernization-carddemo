package com.carddemo.datamigration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.FieldColumn;
import com.carddemo.datamigration.model.RecordMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTest {

  @Test
  void columnNamesFollowV1() {
    assertThat(FieldColumn.snake("custAddrLine1")).isEqualTo("cust_addr_line_1");
    assertThat(FieldColumn.snake("custDobYyyyMmDd")).isEqualTo("cust_dob_yyyy_mm_dd");
    assertThat(FieldColumn.snake("secUsrFiller")).isEqualTo("sec_usr_filler");
  }

  @Test
  void keysAndSurrogates() {
    assertThat(Dataset.TCATBALF.keyColumns())
        .containsExactly("trancat_acct_id", "trancat_type_cd", "trancat_cd");
    assertThat(Dataset.DISCGRP.primaryKey())
        .containsExactly("dis_acct_group_id", "dis_tran_type_cd", "dis_tran_cat_cd");
    assertThat(Dataset.DALYTRAN.primaryKey()).containsExactly("seq_no");
    assertThat(Dataset.USRSEC_ESDS.surrogate(3)).isEqualTo(160);
    assertThat(Dataset.USRSEC_RRDS.surrogate(3)).isEqualTo(3);
    assertThat(Dataset.parseList("acctdata, trantype"))
        .containsExactly(Dataset.TRANTYPE, Dataset.ACCTDATA);
  }

  @Test
  void mapperRoundTripKeepsPicScaleAndPadding() {
    RecordMapper<AccountRecord> m = new RecordMapper<>(AccountRecord.class);
    Object[] v = new Object[m.fields().size()];
    for (int i = 0; i < v.length; i++) {
      v[i] = m.fields().get(i).isNumeric() ? new BigDecimal("12") : "x";
    }
    AccountRecord r = m.fromValues(v);
    assertThat(r.acctCurrBal()).isEqualByComparingTo("12");
    assertThat(r.acctCurrBal().scale()).isEqualTo(2);
    assertThat(r.acctActiveStatus()).isEqualTo("x");
    assertThat(r.acctGroupId()).hasSize(10);
    assertThat(List.of(m.values(r))).hasSize(m.fields().size());
    assertThat(RecordMapper.scaleOf("S9(10)V9(2)")).isEqualTo(2);
    assertThat(RecordMapper.scaleOf("9(4)")).isZero();
  }

  @Test
  void onlyLongBracePrefixedPasswordsAreEncoded() {
    var m = com.carddemo.datamigration.security.UsrsecPasswordMigration.class;
    assertThat(com.carddemo.datamigration.security.UsrsecPasswordMigration.isEncoded("{ABC123 "))
        .as(m.getSimpleName())
        .isFalse();
    assertThat(com.carddemo.datamigration.security.UsrsecPasswordMigration.isEncoded("PASSWORD"))
        .isFalse();
    assertThat(
            com.carddemo.datamigration.security.UsrsecPasswordMigration.isEncoded(
                "{pbkdf2}5d923b44a6d129f3ddf3e3c8d29412723dcbde72445e8ef6bf3b508fbf17fa4ed4d6b99ca763d8dc"))
        .isTrue();
  }
}
