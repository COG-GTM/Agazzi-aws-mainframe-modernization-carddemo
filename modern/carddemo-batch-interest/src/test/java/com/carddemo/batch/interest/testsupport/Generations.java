package com.carddemo.batch.interest.testsupport;

import com.carddemo.contracts.port.GenerationStore;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reads the current generation {@code DSN(0)} of a GDG as records. */
public final class Generations {

  private Generations() {}

  /** Number of the current generation (0 when the GDG is empty); the GDG limit drops old ones. */
  public static int latest(GenerationStore store, String base) {
    return store.resolve(base, 0).map(GenerationStore.Generation::number).orElse(0);
  }

  public static List<String> current(GenerationStore store, String base) {
    return store
        .resolve(base, 0)
        .map(
            g -> {
              try (BufferedReader r =
                  new BufferedReader(
                      new InputStreamReader(store.openInput(g), StandardCharsets.US_ASCII))) {
                return r.lines().filter(l -> !l.isEmpty()).toList();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .orElse(List.of());
  }
}
