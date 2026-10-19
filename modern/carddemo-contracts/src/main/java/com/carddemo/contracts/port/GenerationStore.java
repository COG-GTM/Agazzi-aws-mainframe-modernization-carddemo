package com.carddemo.contracts.port;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/**
 * GDG replacement (plan §1.3, ADR-006): versioned generations {@code <base>/G<nnnn>V00} on the
 * filesystem or an S3 prefix, with retention (LIMIT 5 in DEFGDGB/DEFGDGD).
 */
public interface GenerationStore {

  /** Identifier of one generation of a GDG base. */
  record Generation(String base, int number) {
    /** Mainframe-style name, e.g. {@code TRANSACT.BKUP.G0003V00}. */
    public String dsn() {
      return "%s.G%04dV00".formatted(base, number);
    }
  }

  /** Allocates generation (+1); it becomes visible only after {@link #commit(Generation)}. */
  Generation allocate(String base);

  OutputStream openOutput(Generation generation) throws IOException;

  /** Catalogs the generation (DISP=CATLG) and applies the retention limit. */
  void commit(Generation generation);

  /** Discards an allocated generation (DISP=(,,DELETE) on abend). */
  void discard(Generation generation);

  /** Relative reference: 0 = current, -1 = previous, ... */
  Optional<Generation> resolve(String base, int relative);

  InputStream openInput(Generation generation) throws IOException;

  /** Cataloged generations, oldest first. */
  List<Generation> list(String base);

  /** Retention limit of a base (default 5, as in DEFGDGB/DEFGDGD). */
  default int limit(String base) {
    return 5;
  }
}
