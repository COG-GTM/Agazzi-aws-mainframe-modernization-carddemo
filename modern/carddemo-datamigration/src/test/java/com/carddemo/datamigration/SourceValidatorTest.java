package com.carddemo.datamigration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.datamigration.io.PhysicalLayout;
import com.carddemo.datamigration.io.SourceDataset;
import com.carddemo.datamigration.io.SourceReader;
import com.carddemo.datamigration.io.SourceRecord;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.SourceFormat;
import com.carddemo.datamigration.validate.KeyLookup;
import com.carddemo.datamigration.validate.SourceValidator;
import com.carddemo.datamigration.validate.Violation;
import com.carddemo.datamigration.validate.Violation.Rule;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceValidatorTest {

  private static SourceDataset ds(Dataset d, Record... recs) {
    List<SourceRecord> list = new java.util.ArrayList<>();
    for (Record r : recs) {
      list.add(new SourceRecord(list.size() + 1L, r));
    }
    return new SourceDataset(
        d, SourceFormat.ASCII, "t", "", PhysicalLayout.LINES, list, List.of(), List.of());
  }

  private static CardXrefRecord xref(String card, int cust, long acct) {
    return new CardXrefRecord(card, cust, acct, " ".repeat(14));
  }

  @Test
  void appDataAsciiIsClean() {
    var src =
        new SourceReader()
            .readAll(TestPaths.appData("ASCII"), SourceFormat.ASCII, List.of(Dataset.values()));
    assertThat(new SourceValidator().validate(src, KeyLookup.NONE))
        .noneMatch(v -> v.severity() == Violation.Severity.ERROR);
  }

  @Test
  void duplicateKeysOrphansAndBadDatesAreReported() {
    Map<Dataset, SourceDataset> src = new LinkedHashMap<>();
    src.put(
        Dataset.CARDXREF,
        ds(Dataset.CARDXREF, xref("1111222233334444", 1, 1L), xref("1111222233334444", 1, 1L)));
    String ts = "2022-02-30 10:00:00.000000";
    src.put(
        Dataset.DALYTRAN,
        ds(
            Dataset.DALYTRAN,
            new DailyTransactionRecord(
                "0000000000000001",
                "01",
                1,
                "POS TERM  ",
                " ".repeat(100),
                new BigDecimal("1.00"),
                1,
                " ".repeat(50),
                " ".repeat(50),
                " ".repeat(10),
                "1111222233334444",
                ts,
                " ".repeat(26),
                " ".repeat(20))));
    KeyLookup db =
        (d, cols) ->
            d == Dataset.CUSTDATA ? Optional.of(Set.of("1")) : Optional.of(Set.<String>of());
    List<Violation> v = new SourceValidator().validate(src, db);
    assertThat(v).anyMatch(x -> x.rule() == Rule.DUPLICATE_KEY && x.ordinal() == 2);
    assertThat(v)
        .anyMatch(
            x ->
                x.rule() == Rule.ORPHAN_FK
                    && x.field().equals("xref_card_num")
                    && x.severity() == Violation.Severity.ERROR);
    assertThat(v).noneMatch(x -> x.field().equals("xref_cust_id"));
    assertThat(v)
        .anyMatch(x -> x.rule() == Rule.INVALID_DATE && x.field().equals("DALYTRAN-ORIG-TS"));
    assertThat(v).noneMatch(x -> x.field().equals("DALYTRAN-PROC-TS"));
  }

  @Test
  void childrenOfRejectedParentsAreRejected() {
    var src =
        new SourceReader()
            .readAll(TestPaths.appData("ASCII"), SourceFormat.ASCII, List.of(Dataset.values()));
    List<Violation> v = new java.util.ArrayList<>();
    v.add(
        new Violation(
            Dataset.ACCTDATA, 1, Rule.INVALID_DATE, Violation.Severity.ERROR, "x", "test"));
    SourceValidator.cascade(src, v);
    assertThat(v)
        .anyMatch(
            x ->
                x.dataset() == Dataset.CARDDATA
                    && x.rule() == Rule.ORPHAN_FK
                    && x.message().contains("rejected"));
    assertThat(v).anyMatch(x -> x.dataset() == Dataset.CARDXREF && x.rule() == Rule.ORPHAN_FK);
    assertThat(v).anyMatch(x -> x.dataset() == Dataset.TCATBALF && x.rule() == Rule.ORPHAN_FK);
  }
}
