package com.carddemo.datamigration.validate;

import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.validate.Violation.Severity;
import java.util.List;

/**
 * Logical FK between datasets (V1 column comments). ERROR rules become real constraints at cutover
 * ({@code applyConstraints}); WARN rules mirror conditions the batch programs tolerate (e.g.
 * CBTRN02C rejects daily transactions with an unknown card, CBACT04C falls back to the DEFAULT
 * disclosure group).
 */
public record ForeignKey(
    Dataset child,
    List<String> childColumns,
    Dataset parent,
    List<String> parentColumns,
    Severity severity,
    boolean blankAllowed) {

  public static final List<ForeignKey> ALL =
      List.of(
          fk(Dataset.TRANCATG, "tran_type_cd", Dataset.TRANTYPE, "tran_type", Severity.ERROR),
          new ForeignKey(
              Dataset.DISCGRP,
              List.of("dis_tran_type_cd", "dis_tran_cat_cd"),
              Dataset.TRANCATG,
              List.of("tran_type_cd", "tran_cat_cd"),
              Severity.WARN,
              false),
          new ForeignKey(
              Dataset.ACCTDATA,
              List.of("acct_group_id"),
              Dataset.DISCGRP,
              List.of("dis_acct_group_id"),
              Severity.WARN,
              true),
          fk(Dataset.CARDDATA, "card_acct_id", Dataset.ACCTDATA, "acct_id", Severity.ERROR),
          fk(Dataset.CARDXREF, "xref_card_num", Dataset.CARDDATA, "card_num", Severity.ERROR),
          fk(Dataset.CARDXREF, "xref_cust_id", Dataset.CUSTDATA, "cust_id", Severity.ERROR),
          fk(Dataset.CARDXREF, "xref_acct_id", Dataset.ACCTDATA, "acct_id", Severity.ERROR),
          fk(Dataset.TCATBALF, "trancat_acct_id", Dataset.ACCTDATA, "acct_id", Severity.ERROR),
          fk(Dataset.TRANSACT, "tran_card_num", Dataset.CARDXREF, "xref_card_num", Severity.WARN),
          fk(Dataset.TRANSACT, "tran_type_cd", Dataset.TRANTYPE, "tran_type", Severity.WARN),
          fk(
              Dataset.DALYTRAN,
              "dalytran_card_num",
              Dataset.CARDXREF,
              "xref_card_num",
              Severity.WARN),
          new ForeignKey(
              Dataset.DALYTRAN,
              List.of("dalytran_type_cd", "dalytran_cat_cd"),
              Dataset.TRANCATG,
              List.of("tran_type_cd", "tran_cat_cd"),
              Severity.WARN,
              false));

  private static ForeignKey fk(Dataset c, String cc, Dataset p, String pc, Severity s) {
    return new ForeignKey(c, List.of(cc), p, List.of(pc), s, false);
  }

  public String describe() {
    return child.table()
        + "("
        + String.join(",", childColumns)
        + ") -> "
        + parent.table()
        + "("
        + String.join(",", parentColumns)
        + ")";
  }
}
