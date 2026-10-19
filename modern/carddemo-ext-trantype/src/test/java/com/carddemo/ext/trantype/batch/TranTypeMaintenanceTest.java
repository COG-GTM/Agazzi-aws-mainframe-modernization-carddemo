package com.carddemo.ext.trantype.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranTypeMaintenanceTest {

  @Test
  void returnMessageIsNeverClearedLikeCobol() {
    InMemoryTransactionTypeStore store = InMemoryTransactionTypeStore.seeded();
    List<String> out = new ArrayList<>();
    int rc = new TranTypeMaintenance(store).run(List.of("A01Dup", "U77x"), out::add);
    assertThat(rc).isEqualTo(4);
    assertThat(out.get(3)).startsWith("Error accessing: TRANSACTION_TYPE table. SQLCODE: -803");
    assertThat(out.get(6)).startsWith("No records found.TRANSACTION_TYPE table. SQLCODE: -803");
    assertThat(out.get(6)).hasSize(80);
  }

  @Test
  void openFailure() {
    List<String> out = new ArrayList<>();
    assertThat(new TranTypeMaintenance(InMemoryTransactionTypeStore.seeded()).openFailed(out::add))
        .isEqualTo(12);
    assertThat(out).containsExactly("OPEN FILE NOT OK");
  }
}
