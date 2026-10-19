package com.carddemo.ext.trantype.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.ext.trantype.store.CategoryRow;
import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranTypeConsistencyCheckTest {

  @Test
  void seedIsConsistentAndMatchesItsOwnExtract() {
    InMemoryTransactionTypeStore store = InMemoryTransactionTypeStore.seeded();
    TranTypeConsistencyCheck check = new TranTypeConsistencyCheck(store);
    List<String> types = check.expectedTypeExtract();
    List<String> cats = check.expectedCategoryExtract();
    assertThat(types).hasSize(7).allMatch(r -> r.length() == 60);
    assertThat(types.get(0)).isEqualTo("01" + String.format("%-50s", "Purchase") + "00000000");
    assertThat(cats.get(0))
        .isEqualTo("010001" + String.format("%-50s", "Regular Sales Draft") + "0000");
    TranTypeConsistencyCheck.Report r = check.check(types, cats);
    assertThat(r.findings()).isEmpty();
    assertThat(r.returnCode()).isZero();
    assertThat(r.types()).isEqualTo(7);
    assertThat(r.categories()).isEqualTo(18);
  }

  @Test
  void reportsOrphansBlanksAndExtractDifferences() {
    InMemoryTransactionTypeStore store = InMemoryTransactionTypeStore.seeded();
    store.types.put("08", " ");
    store.types.put("09", "x".repeat(51));
    store.categories.put("990001", new CategoryRow("99", 1, "Orphan", "0000"));
    store.categories.put("010009", new CategoryRow("01", 9, "", "0000"));
    TranTypeConsistencyCheck check = new TranTypeConsistencyCheck(store);
    List<String> types = new ArrayList<>(check.expectedTypeExtract());
    types.remove(0);
    types.set(0, types.get(0).replace("Payment", "Paymant"));
    types.add("55" + " ".repeat(58));
    TranTypeConsistencyCheck.Report r = check.check(types, null);
    assertThat(r.findings())
        .contains(
            "TYPE 08 BLANK DESCRIPTION",
            "TYPE 09 DESCRIPTION LONGER THAN PIC X(50)",
            "CATEGORY 990001 ORPHAN (NO TYPE)",
            "CATEGORY 010009 BLANK DESCRIPTION",
            "TRANTYPE 01 MISSING IN EXTRACT",
            "TRANTYPE 02 DIFFERS",
            "TRANTYPE 55 NOT IN DATABASE");
    assertThat(r.returnCode()).isEqualTo(4);
  }
}
