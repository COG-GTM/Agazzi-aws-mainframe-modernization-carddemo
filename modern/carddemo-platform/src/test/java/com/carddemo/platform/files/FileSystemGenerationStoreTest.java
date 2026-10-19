package com.carddemo.platform.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.GenerationStore.Generation;
import java.io.OutputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemGenerationStoreTest {

  @TempDir Path root;

  @Test
  void keepsLastFiveGenerationsAndResolvesRelative() throws Exception {
    FileSystemGenerationStore store = new FileSystemGenerationStore(root);
    for (int i = 0; i < 7; i++) {
      Generation g = store.allocate("TRANSACT.BKUP");
      try (OutputStream out = store.openOutput(g)) {
        out.write(("gen" + i).getBytes());
      }
      store.commit(g);
    }
    assertThat(store.list("TRANSACT.BKUP"))
        .extracting(Generation::number)
        .containsExactly(3, 4, 5, 6, 7);
    assertThat(store.resolve("TRANSACT.BKUP", 0)).get().extracting(Generation::number).isEqualTo(7);
    assertThat(store.resolve("TRANSACT.BKUP", -1))
        .get()
        .extracting(Generation::number)
        .isEqualTo(6);
    assertThat(store.resolve("TRANSACT.BKUP", -5)).isEmpty();
    assertThat(new Generation("TRANSACT.BKUP", 7).dsn()).isEqualTo("TRANSACT.BKUP.G0007V00");
  }

  @Test
  void discardedGenerationIsNotCataloged() {
    FileSystemGenerationStore store = new FileSystemGenerationStore(root);
    Generation g = store.allocate("SYSTRAN");
    store.discard(g);
    assertThat(store.list("SYSTRAN")).isEmpty();
  }
}
